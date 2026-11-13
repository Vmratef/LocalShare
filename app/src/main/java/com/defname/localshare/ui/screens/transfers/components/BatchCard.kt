// SPDX-FileCopyrightText: 2026 2026 defname
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.defname.localshare.ui.screens.transfers.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import com.defname.localshare.util.ApkIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.PlayCircle
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SwapHoriz
import com.defname.localshare.ui.components.GlassCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.defname.localshare.domain.model.DraftUploadBatch
import com.defname.localshare.domain.model.UploadBatch
import com.defname.localshare.domain.model.UploadFile
import com.defname.localshare.domain.model.UploadFileState
import com.defname.localshare.util.sizeAsString

@Composable
fun DraftBatchCard(
    batch: DraftUploadBatch,
    onGrant: () -> Unit,
    onDeny: () -> Unit,
    onRelay: () -> Unit = onGrant,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Upload Request",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "From: ${batch.clientIp}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${batch.files.size} files",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total Size: ${batch.files.sumOf { it.size }.sizeAsString()}",
                    style = MaterialTheme.typography.bodyMedium
                )
                FilledTonalIconButton(
                    onClick = onDeny,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Deny")
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onRelay,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Relay only")
                }
                Button(
                    onClick = onGrant,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save here")
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "Relay only: other devices can download it, but it is not saved on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    Spacer(Modifier.height(8.dp))
                    batch.files.forEach { file ->
                        FileListRow(file.name, file.size)
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveBatchCard(
    batch: UploadBatch,
    onSaveToDevice: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isFinished = batch.files.all { it.state is UploadFileState.Finished }
                Icon(
                    imageVector = if (isFinished) Icons.Default.DoneAll else Icons.Default.Sync,
                    contentDescription = null,
                    tint = if (isFinished) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Transfer: ${batch.batchId.take(8)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Source: ${batch.clientIp}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                val progress = if (batch.files.isEmpty()) 0f else {
                    batch.files.sumOf { 
                        when (val s = it.state) {
                            is UploadFileState.Finished -> it.size
                            is UploadFileState.Uploading -> s.progress
                            else -> 0
                        }
                    }.toFloat() / (batch.files.sumOf { it.size }.toFloat())
                }
                
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = progress,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp
                    )
                }
            }

            if (!expanded && batch.files.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    batch.files.take(4).forEach { file -> UploadThumbnail(file, 56.dp) }
                    if (batch.files.size > 4) {
                        Text(
                            text = "+${batch.files.size - 4}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (batch.relayOnly) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Relay only - not saved on this device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (batch.files.isNotEmpty() && batch.files.all { it.state is UploadFileState.Finished }) {
                        TextButton(onClick = onSaveToDevice) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save here too")
                        }
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    batch.files.forEach { file ->
                        FileProgressRow(file)
                    }
                }
            }
        }
    }
}

@Composable
private fun FileListRow(name: String, size: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        Text(
            text = size.sizeAsString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FileProgressRow(file: UploadFile) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                UploadThumbnail(file, 40.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
            Text(
                text = file.size.sizeAsString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (file.state is UploadFileState.Uploading) {
            LinearProgressIndicator(
            progress = { file.state.progress.toFloat() / file.size.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(2.dp),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = ProgressIndicatorDefaults.linearTrackColor,
            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
            )
        }
    }
}

// Preview of a received file once it is fully saved:
// images/videos show a thumbnail, apps show their icon, everything else a status icon.
// Tapping a finished file opens it with the matching app (viewer, player, installer).
@Composable
private fun UploadThumbnail(file: UploadFile, size: Dp = 40.dp) {
    val context = LocalContext.current
    val isImage = file.mimeType.startsWith("image/")
    val isVideo = file.mimeType.startsWith("video/")
    val isApk = ApkIcon.isApk(file.name, file.mimeType)
    val isFinished = file.uri != null && file.state is UploadFileState.Finished
    val canPreview = (isImage || isVideo) && isFinished

    val apkIcon by produceState<ImageBitmap?>(initialValue = null, file.uri, isFinished) {
        value = if (isApk && isFinished) {
            withContext(Dispatchers.IO) {
                ApkIcon.loadBitmap(context, file.uri, file.size)?.asImageBitmap()
            }
        } else {
            null
        }
    }

    val boxModifier = Modifier
        .size(size)
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .let { m ->
            if (isFinished) m.clickable { openReceivedFile(context, file) } else m
        }

    Box(modifier = boxModifier, contentAlignment = Alignment.Center) {
        val appIcon = apkIcon
        if (canPreview) {
            val imageLoader = remember(context) {
                ImageLoader.Builder(context)
                    .components { add(VideoFrameDecoder.Factory()) }
                    .build()
            }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file.uri)
                    .videoFrameMillis(1000)
                    .size(256)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (isVideo) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.size(size / 2)
                )
            }
        } else if (appIcon != null) {
            Image(
                bitmap = appIcon,
                contentDescription = file.name,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            )
        } else {
            Icon(
                imageVector = getStatusIcon(file.state),
                contentDescription = null,
                modifier = Modifier.size(size / 2),
                tint = getStatusColor(file.state)
            )
        }
    }
}

private fun openReceivedFile(context: Context, file: UploadFile) {
    val uri = file.uri ?: return
    try {
        val shareUri = if (uri.scheme == "file") {
            val path = uri.path ?: return
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", File(path))
        } else {
            uri
        }
        val mime = when {
            ApkIcon.isApk(file.name, file.mimeType) -> ApkIcon.APK_MIME
            file.mimeType.isBlank() -> "*/*"
            else -> file.mimeType
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(shareUri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun getStatusIcon(state: UploadFileState): ImageVector = when (state) {
    is UploadFileState.Pending -> Icons.Default.Schedule
    is UploadFileState.Uploading -> Icons.Default.CloudUpload
    is UploadFileState.Finished -> Icons.Default.CheckCircle
    is UploadFileState.Error -> Icons.Default.Error
    is UploadFileState.Canceled -> Icons.Default.Block
}

@Composable
private fun getStatusColor(state: UploadFileState) = when (state) {
    is UploadFileState.Pending -> MaterialTheme.colorScheme.onSurfaceVariant
    is UploadFileState.Uploading -> MaterialTheme.colorScheme.secondary
    is UploadFileState.Finished -> MaterialTheme.colorScheme.tertiary
    is UploadFileState.Error -> MaterialTheme.colorScheme.error
    is UploadFileState.Canceled -> MaterialTheme.colorScheme.outline
}
