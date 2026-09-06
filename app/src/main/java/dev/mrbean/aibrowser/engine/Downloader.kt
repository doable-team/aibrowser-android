package dev.mrbean.aibrowser.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.collections.ArrayDeque

/** Thrown when the server returns an unexpected status or the download corrupts. */
class DownloadException(val status: Int, message: String) : IOException(message)

/**
 * Resumable HTTP downloader. Writes to `target.path + ".part"`; when that file
 * already has data it sends `Range: bytes=N-` and appends on a 206, restarting
 * from scratch when the server ignores the Range (200). Cancellation keeps the
 * .part file so a later attempt can resume.
 */
class Downloader {

    private companion object {
        const val CONNECT_TIMEOUT_MS = 30_000
        const val READ_TIMEOUT_MS = 30_000
        const val COPY_BUFFER_SIZE = 64 * 1024
        const val MAX_REDIRECTS = 10
        const val PROGRESS_INTERVAL_NS = 250_000_000L
        const val SPEED_WINDOW_NS = 2_000_000_000L
        const val NANOS_PER_SEC = 1_000_000_000L
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }

    suspend fun download(
        url: String,
        target: File,
        onProgress: (bytesDone: Long, bytesTotal: Long, bytesPerSecond: Long) -> Unit = { _, _, _ -> },
    ): Unit = withContext(Dispatchers.IO) {
        val partFile = File(target.path + ".part")
        var offset = if (partFile.isFile) partFile.length() else 0L

        var conn: HttpURLConnection? = null
        try {
            conn = openWithRedirects(url, offset)

            val status = conn.responseCode
            val append: Boolean
            val bytesTotal: Long
            when (status) {
                HttpURLConnection.HTTP_PARTIAL -> {
                    append = true
                    bytesTotal = contentRangeTotal(conn.getHeaderField("Content-Range"))
                }
                HttpURLConnection.HTTP_OK -> {
                    append = false
                    offset = 0L
                    val length = conn.getHeaderFieldInt("Content-Length", -1)
                    bytesTotal = if (length < 0) -1L else length + offset
                }
                else -> {
                    val message = conn.responseMessage?.takeIf { it.isNotBlank() } ?: "HTTP $status"
                    throw DownloadException(status, message)
                }
            }

            val input = conn.inputStream
            try {
                val output = FileOutputStream(partFile, append)
                try {
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    var done = if (append) offset else 0L
                    val window = ArrayDeque<LongArray>()
                    var lastEmit = 0L

                    fun emit(now: Long) {
                        window.addLast(longArrayOf(now, done))
                        while (window.size > 1 && now - window.first()[0] > SPEED_WINDOW_NS) {
                            window.removeFirst()
                        }
                        val base = window.first()
                        val dt = now - base[0]
                        val db = done - base[1]
                        val bps = if (dt <= 0) 0L else db * NANOS_PER_SEC / dt
                        onProgress(done, bytesTotal, bps)
                    }

                    while (true) {
                        if (!currentCoroutineContext().isActive) throw CancellationException()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        val now = System.nanoTime()
                        if (now - lastEmit >= PROGRESS_INTERVAL_NS) {
                            lastEmit = now
                            emit(now)
                        }
                    }
                    emit(System.nanoTime())
                } finally {
                    output.close()
                }
            } finally {
                input.close()
            }

            val finalSize = partFile.length()
            if (bytesTotal >= 0 && finalSize != bytesTotal) {
                throw DownloadException(status, "size mismatch: expected $bytesTotal bytes, got $finalSize")
            }

            target.delete()
            if (!partFile.renameTo(target)) {
                partFile.copyTo(target, overwrite = true)
                partFile.delete()
            }
        } finally {
            conn?.disconnect()
        }
    }

    private fun openWithRedirects(url: String, offset: Long): HttpURLConnection {
        var current = url
        var sendRange = offset > 0
        var hops = 0
        while (true) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                if (sendRange) setRequestProperty("Range", "bytes=$offset-")
            }
            val code = connection.responseCode
            if (code !in REDIRECT_CODES) return connection

            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (location.isNullOrBlank()) {
                throw DownloadException(code, "redirect without a Location header")
            }
            hops++
            if (hops > MAX_REDIRECTS) {
                throw DownloadException(code, "too many redirects")
            }
            current = URL(URL(current), location).toString()
            // The request is a body-less GET, so the resume offset survives
            // every redirect kind, including the 302s release hosts use.
            sendRange = offset > 0
        }
    }

    private fun contentRangeTotal(header: String): Long {
        if (header.isNullOrBlank()) return -1L
        val slash = header.lastIndexOf('/')
        if (slash < 0) return -1L
        return header.substring(slash + 1).trim().toLongOrNull() ?: -1L
    }
}