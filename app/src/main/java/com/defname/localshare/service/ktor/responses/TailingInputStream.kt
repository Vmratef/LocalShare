package com.defname.localshare.service.ktor.responses

import com.defname.localshare.data.LiveUpload
import java.io.IOException
import java.io.InputStream

/**
 * Reads a file that is STILL BEING WRITTEN by an upload. When the reader catches up with the
 * writer it waits for more bytes instead of reporting end-of-file, until [expectedSize] bytes
 * were delivered. Aborts if the upload failed/was canceled or the downloading client is gone.
 */
class TailingInputStream(
    private val delegate: InputStream,
    private val live: LiveUpload,
    private val expectedSize: Long,
    private val isCancelled: () -> Boolean = { false }
) : InputStream() {

    private var consumed = 0L

    override fun read(): Int {
        val one = ByteArray(1)
        val n = read(one, 0, 1)
        return if (n == -1) -1 else one[0].toInt() and 0xff
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        while (true) {
            if (consumed >= expectedSize) return -1
            val toRead = minOf(len.toLong(), expectedSize - consumed).toInt()
            val n = delegate.read(b, off, toRead)
            if (n > 0) {
                consumed += n
                return n
            }
            if (live.failed) throw IOException("Upload was canceled or failed")
            if (isCancelled()) throw IOException("Download client is gone")
            if (live.finished) {
                val last = delegate.read(b, off, toRead)
                if (last > 0) {
                    consumed += last
                    return last
                }
                return -1
            }
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted")
            }
        }
    }

    override fun close() {
        delegate.close()
    }
}
