package com.defname.localshare.domain.manager

import android.net.Uri
import com.defname.localshare.IconMap
import com.defname.localshare.data.FileStorageProvider
import com.defname.localshare.data.ReceivedFilesStore
import com.defname.localshare.data.ServiceRepository
import com.defname.localshare.data.UploadRepository
import com.defname.localshare.domain.model.DraftUploadBatch
import com.defname.localshare.domain.model.EventEnvelop
import com.defname.localshare.domain.model.FileInfo
import com.defname.localshare.domain.model.UploadFile
import com.defname.localshare.domain.model.UploadFileState
import com.defname.localshare.domain.model.UploadStatus
import com.defname.localshare.domain.model.WebEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.InputStream

class UploadManager(
    private val uploadRepository: UploadRepository,
    private val fileStorageProvider: FileStorageProvider,
    private val featureFlagsProvider: FeatureFlagsProvider,
    private val serviceRepository: ServiceRepository,
    private val receivedFilesStore: ReceivedFilesStore
) {
    private val _openUploadRequests = MutableStateFlow(emptyList<DraftUploadBatch>())
    val openUploadRequests = _openUploadRequests.asStateFlow()

    private val _receivedFileEvents = MutableSharedFlow<Pair<FileInfo, String>>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val receivedFileEvents = _receivedFileEvents.asSharedFlow()

    private val _withdrawnFileEvents = MutableSharedFlow<Pair<String, String>>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val withdrawnFileEvents = _withdrawnFileEvents.asSharedFlow()

    private val _uploadEvents = MutableSharedFlow<Pair<EventEnvelop, String>>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val uploadEvents = _uploadEvents.asSharedFlow()

    private fun fileIdFor(uri: Uri): String = Integer.toHexString(uri.toString().hashCode()).lowercase()

    fun onNewUploadRequest(clientIp: String, draftId: Int, files: List<UploadFile>) {
        if (findRequest(clientIp, draftId) != null || files.isEmpty()) return
        addRequest(clientIp, draftId, files)
    }

    suspend fun onRequestDenied(clientIp: String, draftId: Int) {
        removeRequest(clientIp, draftId) ?: return
        _uploadEvents.emit(EventEnvelop(event = WebEvent.UploadDenied(draftId)) to clientIp)
    }

    /**
     * @param relayOnly true: keep the upload in app cache and offer it to OTHER devices only.
     *                  false: save it in Downloads and offer it to other devices too.
     */
    suspend fun onRequestAccepted(clientIp: String, draftId: Int, relayOnly: Boolean = false) {
        val draft = removeRequest(clientIp, draftId) ?: return
        val batchId = java.util.UUID.randomUUID().toString().take(6)

        val fileErrors = mutableListOf<String>()
        val fileUris = draft.files.map { file ->
            val uri = if (relayOnly) {
                fileStorageProvider.createRelayFile(file.name)
            } else {
                fileStorageProvider.createOutputFile(file.name, file.mimeType, null)
            }
            if (uri == null) {
                fileErrors += file.checksum
                return@map null
            }
            file.checksum to uri
        }.filterNotNull().toMap()

        if (fileUris.isEmpty()) {
            emitUploadError(clientIp, draft.draftId.toString(), "No files to upload.")
            return
        }

        uploadRepository.initNewUploadBatch(batchId, draft, fileUris, relayOnly)
        _uploadEvents.emit(EventEnvelop(event = WebEvent.UploadAccepted(draftId, batchId)) to clientIp)

        // Publish the incoming-file offer immediately. Downloads will tail the growing file.
        draft.files.forEach { file ->
            val uri = fileUris[file.checksum] ?: return@forEach
            offerFile(clientIp, file, uri)
        }

        fileErrors.forEach { fileHash ->
            emitFileError(clientIp, batchId, fileHash, "File cannot be created.")
        }
    }

    /** Cancel unfinished files and withdraw their offers from every receiving browser. */
    suspend fun onUploadCanceledByClient(clientIp: String, batchId: String) {
        val batch = uploadRepository.getBatch(batchId) ?: return
        if (batch.clientIp != clientIp) return

        var anyFinished = false
        batch.files.forEach { file ->
            val uri = file.uri
            if (file.state is UploadFileState.Finished) {
                anyFinished = true
                return@forEach
            }
            if (uri != null) {
                withdrawOffer(clientIp, uri)
                fileStorageProvider.deleteFile(uri)
            }
            uploadRepository.updateFileState(batchId, file.checksum, UploadFileState.Canceled)
        }
        if (!anyFinished) uploadRepository.removeBatch(batchId)
    }

    suspend fun onFileUpload(clientIp: String, batchId: String, fileHash: String, inputStream: InputStream) {
        val batch = uploadRepository.getBatch(batchId) ?: return
        if (batch.clientIp != clientIp) return

        val file = batch.files.find { it.checksum == fileHash } ?: return
        val uri = file.uri ?: return
        if (file.state is UploadFileState.Uploading || file.state is UploadFileState.Finished) return

        val outputStream = fileStorageProvider.openOutputStream(uri)
        if (outputStream == null) {
            withdrawOffer(clientIp, uri)
            emitFileError(clientIp, batchId, fileHash, "File can not be written.")
            return
        }

        try {
            uploadRepository.updateFileState(batchId, fileHash, UploadFileState.Uploading(0))
            var totalBytesRead = 0L

            withContext(Dispatchers.IO) {
                val buffer = ByteArray(64 * 1024)
                var lastReport = 0L
                outputStream.use { out ->
                    while (true) {
                        val bytesRead = inputStream.read(buffer)
                        if (bytesRead == -1) break
                        out.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        val now = System.nanoTime()
                        if (now - lastReport > 300_000_000L) {
                            lastReport = now
                            uploadRepository.updateFileState(batchId, fileHash, UploadFileState.Uploading(totalBytesRead))
                            val event = EventEnvelop(
                                event = WebEvent.UploadUpdated(
                                    id = batchId,
                                    fileChecksum = fileHash,
                                    status = UploadStatus.UPLOADING,
                                    progress = totalBytesRead
                                )
                            )
                            _uploadEvents.emit(event to clientIp)
                        }
                    }
                }
            }

            if (totalBytesRead != file.size) {
                withdrawOffer(clientIp, uri)
                fileStorageProvider.deleteFile(uri)
                uploadRepository.updateFileState(batchId, fileHash, UploadFileState.Error)
                emitFileError(clientIp, batchId, fileHash, "File size mismatch: expected ${file.size} bytes, received $totalBytesRead bytes.")
                return
            }

            if (!batch.relayOnly) {
                fileStorageProvider.finalizeFile(uri)
                receivedFilesStore.record(uri, file.name, file.size, clientIp)
            }
            serviceRepository.liveUploadFor(fileIdFor(uri))?.finished = true
            uploadRepository.updateFileState(batchId, fileHash, UploadFileState.Finished)

            _uploadEvents.emit(
                EventEnvelop(
                    event = WebEvent.UploadUpdated(
                        id = batchId,
                        fileChecksum = fileHash,
                        status = UploadStatus.FINISHED
                    )
                ) to clientIp
            )
        } catch (e: Exception) {
            withdrawOffer(clientIp, uri)
            fileStorageProvider.deleteFile(uri)
            uploadRepository.updateFileState(batchId, fileHash, UploadFileState.Error)
            emitFileError(clientIp, batchId, fileHash, "Upload failed: ${e.message}")
        }
    }

    suspend fun saveRelayBatchToDevice(batchId: String): Int {
        val batch = uploadRepository.getBatch(batchId) ?: return 0
        if (!batch.relayOnly) return 0

        var saved = 0
        var allSaved = true
        val newFiles = batch.files.map { file ->
            val source = file.uri
            if (source == null || file.state !is UploadFileState.Finished) return@map file
            val target = fileStorageProvider.createOutputFile(file.name, file.mimeType, null)
            if (target == null) {
                allSaved = false
                return@map file
            }
            if (fileStorageProvider.copyFile(source, target)) {
                fileStorageProvider.finalizeFile(target)
                receivedFilesStore.record(target, file.name, file.size, batch.clientIp)
                saved++
                file.copy(uri = target)
            } else {
                fileStorageProvider.deleteFile(target)
                allSaved = false
                file
            }
        }
        uploadRepository.updateBatch(batch.copy(files = newFiles, relayOnly = !(saved > 0 && allSaved)))
        return saved
    }

    private suspend fun offerFile(clientIp: String, file: UploadFile, uri: Uri) {
        val mime = file.mimeType.ifBlank { "application/octet-stream" }
        val info = FileInfo(
            id = fileIdFor(uri),
            uri = uri,
            name = file.name,
            size = file.size,
            mimeType = mime,
            iconFile = IconMap.getIcon(mime)
        )
        serviceRepository.beginLiveUpload(info.id, file.size)
        serviceRepository.addReceivedFile(info, clientIp)
        _receivedFileEvents.emit(info to clientIp)
    }

    private suspend fun withdrawOffer(clientIp: String, uri: Uri) {
        val id = fileIdFor(uri)
        serviceRepository.liveUploadFor(id)?.failed = true
        serviceRepository.removeReceivedFile(id)
        _withdrawnFileEvents.emit(id to clientIp)
    }

    private fun findRequest(clientIp: String, draftId: Int) =
        _openUploadRequests.value.find { it.draftId == draftId && it.clientIp == clientIp }

    private fun addRequest(clientIp: String, draftId: Int, files: List<UploadFile>) {
        if (!featureFlagsProvider.uploadFeature.value) return
        _openUploadRequests.update { it + DraftUploadBatch(draftId, clientIp, files) }
    }

    private fun removeRequest(clientIp: String, draftId: Int): DraftUploadBatch? {
        val request = findRequest(clientIp, draftId) ?: return null
        _openUploadRequests.update { it - request }
        return request
    }

    private suspend fun emitUploadError(clientIp: String, id: String, message: String? = null) {
        _uploadEvents.emit(EventEnvelop(event = WebEvent.UploadError(id, message)) to clientIp)
    }

    private suspend fun emitFileError(clientIp: String, requestId: String, fileHash: String, message: String) {
        _uploadEvents.emit(
            EventEnvelop(
                event = WebEvent.UploadUpdated(
                    id = requestId,
                    fileChecksum = fileHash,
                    status = UploadStatus.ERROR,
                    message = message
                )
            ) to clientIp
        )
    }
}
