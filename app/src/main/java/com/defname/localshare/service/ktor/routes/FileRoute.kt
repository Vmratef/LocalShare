// SPDX-FileCopyrightText: 2026 2026 defname
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.defname.localshare.service.ktor.routes

import android.content.Context
import com.defname.localshare.data.ServiceRepository
import com.defname.localshare.service.ServerSecurityHandler
import com.defname.localshare.service.ktor.responses.sendFile
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.flow.first

fun Route.getFile(
    securityHandler: ServerSecurityHandler,
    serviceRepository: ServiceRepository,
    context: Context
) {
    get("/{token}/file/{file}") {
        val download = (call.queryParameters["download"] != null)
        val requestedFile = call.parameters["file"]

        if (!securityHandler.verifyAccess(call)) {
            return@get call.respondText("No Access.", status = HttpStatusCode.Forbidden)
        }

        val sharedFiles = serviceRepository.fileList.first()
        // Received files are only reachable by their explicit id (never in the ZIP / default file).
        val fileList = if (requestedFile != null) sharedFiles + serviceRepository.receivedFiles.value else sharedFiles

        if (fileList.isEmpty()) {
            return@get call.respondText("No File Selected\n", status = HttpStatusCode.NotFound)
        }

        val fileInfo = if (requestedFile != null) fileList.find { it.id == requestedFile } else fileList[0]
        if (fileInfo == null) {
            return@get call.respondText("File not found.", status = HttpStatusCode.NotFound)
        }

        // A file that another device is still uploading is streamed while it grows.
        val live = serviceRepository.liveUploadFor(fileInfo.id)
            ?.takeIf { !it.finished && !it.failed }

        return@get call.sendFile(
            fileInfo = fileInfo,
            isStream = !download,
            securityHandler = securityHandler,
            onFileNotAvailable = { fileInfo -> serviceRepository.removeFile(fileInfo.uri) },
            context = context,
            liveUpload = live
        )
    }
}
