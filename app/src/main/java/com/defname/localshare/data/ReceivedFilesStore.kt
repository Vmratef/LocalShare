// SPDX-License-Identifier: GPL-3.0-or-later

package com.defname.localshare.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A file that was received by this device (stored in Download/LocalShare). */
data class ReceivedFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
    val time: Long,
    val fromIp: String?
)

/**
 * Lists everything inside Download/LocalShare (so the list survives app restarts) and
 * remembers WHO sent each file and WHEN, which the file system itself does not know.
 */
class ReceivedFilesStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("received_files", Context.MODE_PRIVATE)

    private data class Record(val key: String, val name: String, val size: Long, val ip: String, val time: Long)

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private fun readRecords(): List<Record> {
        val raw = prefs.getString("records", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Record(o.getString("key"), o.optString("name"), o.optLong("size"), o.optString("ip"), o.optLong("time"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeRecords(list: List<Record>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("key", it.key).put("name", it.name).put("size", it.size)
                    .put("ip", it.ip).put("time", it.time)
            )
        }
        prefs.edit().putString("records", arr.toString()).apply()
    }

    /** Remember sender + time of a file that was just received. */
    @Synchronized
    fun record(uri: Uri, name: String, size: Long, fromIp: String) {
        val key = uri.toString()
        val list = (readRecords().filter { it.key != key } + Record(key, name, size, fromIp, System.currentTimeMillis()))
            .takeLast(2000)
        writeRecords(list)
        _version.update { it + 1 }
    }

    /** Forgets the "who/when" info. The files themselves stay on the device. */
    @Synchronized
    fun clearHistory() {
        prefs.edit().remove("records").apply()
        _version.update { it + 1 }
    }

    suspend fun load(): List<ReceivedFile> = withContext(Dispatchers.IO) {
        try {
            val records = readRecords().associateBy { it.key }
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                loadFromMediaStore(records)
            } else {
                loadFromFolders(records)
            }
            result.sortedByDescending { it.time }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun loadFromMediaStore(records: Map<String, Record>): List<ReceivedFile> {
        val out = mutableListOf<ReceivedFile>()
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("${Environment.DIRECTORY_DOWNLOADS}/LocalShare%")
        appContext.contentResolver.query(
            collection, projection, selection, args, "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            while (c.moveToNext()) {
                val uri = ContentUris.withAppendedId(collection, c.getLong(idCol))
                val rec = records[uri.toString()]
                out += ReceivedFile(
                    uri = uri,
                    name = c.getString(nameCol) ?: "file",
                    size = c.getLong(sizeCol),
                    mimeType = c.getString(mimeCol) ?: "application/octet-stream",
                    time = rec?.time ?: (c.getLong(dateCol) * 1000L),
                    fromIp = rec?.ip?.takeIf { it.isNotBlank() }
                )
            }
        }
        return out
    }

    // Android 8/9: plain folders
    private fun loadFromFolders(records: Map<String, Record>): List<ReceivedFile> {
        val roots = listOfNotNull(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "LocalShare"),
            appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, "LocalShare") }
        )
        val out = mutableListOf<ReceivedFile>()
        roots.filter { it.exists() }.forEach { root ->
            root.walkTopDown().filter { it.isFile }.forEach { f ->
                val uri = Uri.fromFile(f)
                val rec = records[uri.toString()]
                val ext = f.extension.lowercase()
                out += ReceivedFile(
                    uri = uri,
                    name = f.name,
                    size = f.length(),
                    mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream",
                    time = rec?.time ?: f.lastModified(),
                    fromIp = rec?.ip?.takeIf { it.isNotBlank() }
                )
            }
        }
        return out
    }
}
