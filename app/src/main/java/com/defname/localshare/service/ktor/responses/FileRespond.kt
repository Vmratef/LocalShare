// SPDX-FileCopyrightText: 2026 2026 defname
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.defname.localshare.service.ktor.responses

import android.content.Context
import android.util.Log
import com.defname.localshare.data.LiveUpload
import com.defname.localshare.domain.model.FileInfo
import com.defname.localshare.service.ServerSecurityHandler
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * @param liveUpload non-null while the file is still being uploaded by another device: the
 *                   download then follows the growing file instead of stopping at its current end.
 */
suspend fun ApplicationCall.sendFile(
    fileInfo: FileInfo,
    isStream: Boolean,
    securityHandler: ServerSecurityHandler,
    onFileNotAvailable: (FileInfo) -> Unit,
    context: Context,
    liveUpload: LiveUpload? = null
) {
    val fileUri = fileInfo.uri
    val fileName = fileInfo.name

    val mimeTypeString = fileInfo.mimeType
    val contentType = ContentType.parse(mimeTypeString)
    val fileSize = fileInfo.size

    val inputStream = try {
        context.contentResolver.openInputStream(fileUri)
    } catch (e: Exception) {
        null
    }

    if (inputStream == null) {
        Log.d("FileServerService", "File not accessible anymore: $fileUri")
        onFileNotAvailable(fileInfo)
        return respond(HttpStatusCode.Gone, "File no longer available")
    }

    try {
        val disposition = if (isStream) ContentDisposition.Inline else ContentDisposition.Attachment
        response.header(HttpHeaders.ContentDisposition, disposition.withParameter(ContentDisposition.Parameters.FileName, fileName).toString())
        respond(object : OutgoingContent.ReadChannelContent() {
            override val contentType = contentType
            override val contentLength = fileSize

            override fun readFrom(): ByteReadChannel {
                return writer(Dispatchers.IO) {
                    val source: InputStream = if (liveUpload != null) {
                        TailingInputStream(inputStream, liveUpload, fileSize) { !isActive }
                    } else {
                        inputStream
                    }
                    source.use { input ->
                        try {
                            val buffer = ByteArray(256 * 1024)
                            var bytesRead: Int

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                if (!securityHandler.mayContinueDownload(this@sendFile.request)) {
                                    val clientIp = this@sendFile.request.local.remoteHost
                                    Log.d("FileServerService", "Abort download: Client $clientIp was banned.")
                                    throw CancellationException("Client banned")
                                }
                                channel.writeFully(buffer, 0, bytesRead)
                            }
                        } catch (e: Exception) {
                            if (e !is CancellationException) {
                                Log.e("FileServerService", "Transfer error", e)
                            }
                            throw e
                        }
                    }
                }.channel
            }
        })
    } catch (e: Exception) {
        Log.e("FileServerService", "Error during file transfer", e)
        withContext(Dispatchers.IO) {
            inputStream.close()
        }
    }
}
