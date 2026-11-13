// SPDX-FileCopyrightText: 2026 2026 defname
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.defname.localshare.ui.screens.files

import android.net.Uri
import com.defname.localshare.IconMap
import com.defname.localshare.ui.screens.files.components.InstalledApp
import com.defname.localshare.util.ApkIcon
import java.io.File
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.defname.localshare.data.ReceivedFile
import com.defname.localshare.data.ReceivedFilesStore
import com.defname.localshare.data.ServiceRepository
import com.defname.localshare.domain.model.FileInfo
import com.defname.localshare.domain.usecase.AddFilesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class InternalState(
    val selectedFiles: Set<Uri> = emptySet(),
    val sortedBy: FilesSortType = FilesSortType.NAME,
    val sortedAscending: Boolean = true,
    val isSortingMenuOpen: Boolean = false,
    val searchQuery: String = "",
)

class FilesViewModel(
    private val serviceRepository: ServiceRepository,
    private val addFilesUseCase: AddFilesUseCase,
    private val receivedFilesStore: ReceivedFilesStore
) : ViewModel() {
    private val _internalState = MutableStateFlow(InternalState())
    private val _receivedFiles = MutableStateFlow<List<ReceivedFile>>(emptyList())

    init {
        // reload whenever a new file was received
        viewModelScope.launch {
            receivedFilesStore.version.collect { refreshReceived() }
        }
    }

    fun refreshReceived() {
        viewModelScope.launch {
            _receivedFiles.value = receivedFilesStore.load()
        }
    }

    val state = combine(
        serviceRepository.fileList,
        _internalState,
        _receivedFiles
    ) { fileList, internalState, receivedFiles ->
        val filteredFiles = fileList.filter { 
            it.name.contains(internalState.searchQuery, ignoreCase = true)
        }
        val sortedFiles = when (internalState.sortedBy) {
            FilesSortType.NAME -> filteredFiles.sortedBy { it.name.lowercase() }
            FilesSortType.SIZE -> filteredFiles.sortedByDescending { it.size }
            FilesSortType.TYPE -> filteredFiles.sortedBy { it.mimeType }
        }.let {
            if (internalState.sortedAscending) it else it.reversed()
        }

        FilesState(
            fileList = sortedFiles,
            selectedFiles = internalState.selectedFiles,
            sortedBy = internalState.sortedBy,
            sortedAscending = internalState.sortedAscending,
            isSortingMenuOpen = internalState.isSortingMenuOpen,
            searchQuery = internalState.searchQuery,
            receivedFiles = receivedFiles
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FilesState()
    )

    fun onToggleSelection(file: FileInfo) {
        val uri = file.uri
        _internalState.update {
            val selection = if (it.selectedFiles.contains(uri)) {
                it.selectedFiles - uri
            } else {
                it.selectedFiles + uri
            }
            it.copy(
                selectedFiles = selection
            )
        }
    }

    fun onClearSelection() {
        _internalState.update { it.copy(selectedFiles = emptySet()) }
    }

    fun onDeleteSelected() {
        val toDelete = _internalState.value.selectedFiles
        serviceRepository.removeFiles(toDelete)
        onClearSelection()
    }

    fun onSortChanged(sortType: FilesSortType) {
        _internalState.update {
            if (it.sortedBy == sortType) {
                it.copy(
                    sortedAscending = !it.sortedAscending
                )
            } else {
                it.copy(
                    sortedBy = sortType,
                    sortedAscending = true
                )
            }
        }
    }

    fun onToggleSortingMenu() {
        _internalState.update { it.copy(isSortingMenuOpen = !it.isSortingMenuOpen) }
    }

    fun addFiles(uris: List<Uri>) {
        addFilesUseCase(uris)
    }

    // Shares installed apps by exposing their .apk file(s) straight from the device.
    fun addApps(apps: List<InstalledApp>) {
        for (app in apps) {
            val safeLabel = app.label
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .trim()
                .ifBlank { app.packageName }
            app.apkPaths.forEachIndexed { index, path ->
                val file = File(path)
                if (file.exists()) {
                    val name = if (index == 0) "$safeLabel.apk" else "${safeLabel}_${file.name}"
                    val uri = Uri.fromFile(file)
                    val mime = ApkIcon.APK_MIME
                    serviceRepository.addFile(
                        FileInfo(
                            id = Integer.toHexString(uri.toString().hashCode()).lowercase(),
                            uri = uri,
                            name = name,
                            size = file.length(),
                            mimeType = mime,
                            iconFile = IconMap.getIcon(mime)
                        )
                    )
                }
            }
        }
    }

    fun selectAll() {
        _internalState.update {
            it.copy(
                selectedFiles = serviceRepository.fileList.value.map { it.uri }.toSet()
            )
        }
    }
}
