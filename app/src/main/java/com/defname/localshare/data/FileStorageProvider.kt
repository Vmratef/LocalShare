package com.defname.localshare.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.media.MediaScannerConnection
import android.os.Environment
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

interface FileStorageProvider {
    suspend fun createOutputFile(fileName: String, mimeType: String, subFolder: String? = null): Uri?
    fun openOutputStream(uri: Uri): OutputStream?
    suspend fun deleteFile(uri: Uri)
    suspend fun finalizeFile(uri: Uri)
}

class MediaStoreStorageProvider(
    private val context: Context
) : FileStorageProvider {

    private val contentResolver: ContentResolver
        get() = context.contentResolver

    private fun safeName(name: String): String {
        val cleaned = name
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
            .trim()
            .trim('.')
        return cleaned.ifBlank { "file" }
    }

    override suspend fun createOutputFile(
        fileName: String,
        mimeType: String,
        subFolder: String?
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val name = safeName(fileName)
            val mime = mimeType.ifBlank { "application/octet-stream" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                createViaMediaStore(name, mime, subFolder)
            } else {
                createViaFile(name, subFolder)
            }
        } catch (e: Exception) {
            // Never crash the app because a file could not be created.
            null
        }
    }

    // Android 10+: /Download/LocalShare/<subFolder> through MediaStore
    private fun createViaMediaStore(name: String, mime: String, subFolder: String?): Uri? {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)

            var relativePath = Environment.DIRECTORY_DOWNLOADS + "/LocalShare"
            if (!subFolder.isNullOrBlank()) {
                relativePath += "/$subFolder"
            }
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        return contentResolver.insert(collection, values)
    }

    // Android 8/9 (API 26-28): MediaStore.Downloads and RELATIVE_PATH do not exist there.
    // Save into the public Download/LocalShare folder (needs WRITE_EXTERNAL_STORAGE);
    // if that permission is missing, fall back to the app's private folder.
    @Synchronized
    private fun createViaFile(name: String, subFolder: String?): Uri? {
        val canUsePublic = ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED &&
            Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED

        val base = if (canUsePublic) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        } else {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        }
        val root = File(base, "LocalShare")
        val dir = if (subFolder.isNullOrBlank()) root else File(root, subFolder)
        if (!dir.exists() && !dir.mkdirs()) return null

        var target = File(dir, name)
        if (target.exists()) {
            val dot = name.lastIndexOf('.')
            val stem = if (dot > 0) name.substring(0, dot) else name
            val ext = if (dot > 0) name.substring(dot) else ""
            var i = 1
            while (target.exists()) {
                target = File(dir, "${stem}_$i$ext")
                i++
            }
        }
        if (!target.createNewFile()) return null
        return Uri.fromFile(target)
    }

    override fun openOutputStream(uri: Uri): OutputStream? {
        return try {
            contentResolver.openOutputStream(uri)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun deleteFile(uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                if (uri.scheme == "file") {
                    uri.path?.let { File(it).delete() }
                } else {
                    contentResolver.delete(uri, null, null)
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    /**
     * Call after writing so the file becomes visible.
     */
    override suspend fun finalizeFile(uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                if (uri.scheme == "file") {
                    // make the file show up in Gallery / file managers
                    uri.path?.let { MediaScannerConnection.scanFile(context, arrayOf(it), null, null) }
                    return@withContext
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                contentResolver.update(uri, values, null, null)
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
