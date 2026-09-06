package dev.mrbean.aibrowser.engine

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom

/** One entry of `data/mcp.tokens`: a human label and its 64-hex token. */
data class ApiToken(
    val label: String,
    val token: String,
)

/**
 * Reads and writes `data/mcp.tokens` (one `<label> <token>` per line), the file
 * the in-rootfs gate re-reads when it changes, so adding or removing a token
 * takes effect at once. Blank lines and lines starting with `#` are skipped on
 * read; rewrites may drop comments.
 */
class TokenStore(private val dataDir: File) {

    private val file: File get() = File(dataDir, "mcp.tokens")

    private val random = SecureRandom()

    fun list(): List<ApiToken> {
        if (!file.isFile) return emptyList()
        return file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val space = line.indexOf(' ')
                if (space <= 0) null
                else ApiToken(line.substring(0, space), line.substring(space + 1).trim())
            }
    }

    /** Generates a fresh 64-hex token for [label] (sanitised) and appends it. */
    fun add(label: String): ApiToken {
        val entry = ApiToken(sanitiseLabel(label), generateToken())
        writeLines((list() + entry).map { "${it.label} ${it.token}" })
        return entry
    }

    /** Removes the line whose token equals [token]; no-op if it is not present. */
    fun remove(token: String) {
        writeLines(list().filterNot { it.token == token }.map { "${it.label} ${it.token}" })
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun sanitiseLabel(label: String): String =
        label.replace(Regex("[^A-Za-z0-9_-]"), "").ifEmpty { "token" }

    private fun writeLines(lines: List<String>) {
        dataDir.mkdirs()
        val content = if (lines.isEmpty()) "" else lines.joinToString("\n") + "\n"
        val temp = File(dataDir, "mcp.tokens.tmp")
        temp.writeText(content)
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