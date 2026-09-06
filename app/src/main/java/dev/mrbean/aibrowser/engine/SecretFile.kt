package dev.mrbean.aibrowser.engine

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 * One of the app's secret files (`data/tunnel.token`, `data/mcp.host`): written
 * atomically (temp file + rename) with mode 600 where the filesystem supports
 * POSIX permissions, and trimmed on both read and write.
 */
class SecretFile(private val file: File) {

    fun readOrEmpty(): String =
        if (file.isFile) file.readText().trim() else ""

    fun write(value: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(value.trim())
        setMode600(temp)
        // renameTo is atomic on the same filesystem; copy as a fallback.
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }

    private fun setMode600(f: File) {
        runCatching {
            Files.setPosixFilePermissions(
                f.toPath(),
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
    }
}