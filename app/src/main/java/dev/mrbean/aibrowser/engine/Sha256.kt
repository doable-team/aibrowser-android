package dev.mrbean.aibrowser.engine

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/** Streaming SHA-256 over a file, reporting bytes processed through [onProgress]. */
object Sha256 {

    private const val BUFFER_SIZE = 256 * 1024

    fun hex(file: File, onProgress: (bytesDone: Long) -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            var done = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                done += read
                onProgress(done)
            }
        }
        val bytes = digest.digest()
        val hex = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            hex.append("0123456789abcdef"[(b.toInt() ushr 4) and 0xf])
            hex.append("0123456789abcdef"[b.toInt() and 0xf])
        }
        return hex.toString()
    }
}