// SPDX-License-Identifier: GPL-3.0-or-later

package com.defname.localshare.ui.screens.files.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.defname.localshare.IconMap
import com.defname.localshare.data.ReceivedFile
import com.defname.localshare.domain.model.FileInfo
import com.defname.localshare.ui.components.FileIcon
import com.defname.localshare.ui.components.GlassCard
import com.defname.localshare.ui.components.HintCard
import com.defname.localshare.util.ApkIcon
import com.defname.localshare.util.sizeAsString
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun ReceivedFileList(
    files: List<ReceivedFile>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    if (files.isEmpty()) {
        HintCard(
            "No received files yet. Files that other devices send to this device appear here.",
            modifier = modifier.padding(horizontal = 16.dp)
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(files, key = { it.uri.toString() }) { file ->
            val info = remember(file.uri) {
                FileInfo(
                    id = file.uri.toString(),
                    uri = file.uri,
                    name = file.name,
                    size = file.size,
                    mimeType = file.mimeType,
                    iconFile = IconMap.getIcon(file.mimeType)
                )
            }
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .clickable { openReceived(context, file) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FileIcon(info, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${file.size.sizeAsString()}  •  ${dateFormat.format(Date(file.time))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "From: ${file.fromIp ?: "unknown device"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

private fun openReceived(context: Context, file: ReceivedFile) {
    try {
        val uri = if (file.uri.scheme == "file") {
            val path = file.uri.path ?: return
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", File(path))
        } else {
            file.uri
        }
        val mime = when {
            ApkIcon.isApk(file.name, file.mimeType) -> ApkIcon.APK_MIME
            file.mimeType.isBlank() -> "*/*"
            else -> file.mimeType
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
    }
}
