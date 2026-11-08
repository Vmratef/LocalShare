package com.defname.localshare.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import java.io.File

object ApkIcon {
    const val APK_MIME = "application/vnd.android.package-archive"
    private const val MAX_COPY_BYTES = 300L * 1024 * 1024

    fun isApk(name: String, mimeType: String): Boolean =
        mimeType == APK_MIME || name.endsWith(".apk", ignoreCase = true)

    /** Reads the launcher icon out of an .apk file (installed or not). Returns null if it can't. */
    fun loadBitmap(context: Context, uri: Uri?, size: Long, px: Int = 192): Bitmap? {
        if (uri == null) return null
        return try {
            val path: String = if (uri.scheme == "file") {
                uri.path ?: return null
            } else {
                if (size > MAX_COPY_BYTES) return null
                val tmp = File(context.cacheDir, "apk_preview_" + uri.toString().hashCode() + ".apk")
                if (!tmp.exists() || tmp.length() != size) {
                    val input = context.contentResolver.openInputStream(uri) ?: return null
                    input.use { ins -> tmp.outputStream().use { out -> ins.copyTo(out) } }
                }
                tmp.absolutePath
            }
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(path, 0) ?: return null
            val appInfo = info.applicationInfo ?: return null
            appInfo.sourceDir = path
            appInfo.publicSourceDir = path
            appInfo.loadIcon(pm).toBitmap(px, px)
        } catch (e: Exception) {
            null
        }
    }
}
