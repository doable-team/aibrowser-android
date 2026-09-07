package dev.mrbean.aibrowser.engine

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** One installed extension directory, as shown on the Settings screen. */
data class InstalledExtension(
    val directory: String,   // the directory name under data/extensions
    val name: String,        // manifest "name", or the directory name when unreadable/localised
    val version: String,     // manifest "version", empty when unreadable
)

/** Thrown when a picked file is not an extension we can install. */
class ExtensionException(message: String) : Exception(message)

/**
 * Installs and removes unpacked Chrome extensions under `data/extensions`,
 * the directory the chromium service passes to `--load-extension`.
 *
 * Pure JVM so it can be unit-tested without Android.
 */
class ExtensionInstaller(private val paths: Paths) {

    private val extensionsDir: File get() = File(paths.data, "extensions")

    /** Every directory under data/extensions, sorted by directory name. */
    fun list(): List<InstalledExtension> {
        val dir = extensionsDir
        return dir.listFiles()
            ?.filter { it.isDirectory && it.name != STAGING_NAME }
            ?.sortedBy { it.name }
            ?.map { d ->
                val manifest = File(d, "manifest.json")
                if (manifest.isFile) {
                    val (name, version) = readMetadata(manifest, d.name)
                    InstalledExtension(d.name, name, version)
                } else {
                    InstalledExtension(d.name, d.name, "")
                }
            }
            ?: emptyList()
    }

    /**
     * Unpacks [input] into `data/extensions/<dir>` and returns its metadata.
     *
     * Throws [ExtensionException] with an operator-readable message when the
     * archive is not an extension we accept.
     */
    fun install(fileName: String, input: InputStream): InstalledExtension {
        val dirName = deriveDirName(fileName)
        val staging = File(extensionsDir, STAGING_NAME)
        try {
            if (staging.exists()) staging.deleteRecursively()
            staging.mkdirs()
            extract(input.openZip(), staging)

            // A wrapper directory is common: crx4chrome and similar pack the
            // extension under one top-level folder. Keep only that folder.
            val payload = findPayload(staging)

            val manifest = File(payload, "manifest.json")
            val (name, version) = readMetadata(manifest, dirName)

            val target = File(extensionsDir, dirName)
            if (target.exists()) target.deleteRecursively()
            // renameTo is atomic on the same filesystem; copy as a fallback.
            val moved = if (payload == staging) {
                staging.renameTo(target)
            } else {
                payload.renameTo(target)
            }
            if (!moved) {
                payload.copyRecursively(target, overwrite = true)
            }
            return InstalledExtension(dirName, name, version)
        } catch (e: ExtensionException) {
            throw e
        } catch (e: Exception) {
            // The exception type is kept: several IO failures carry only a
            // path as their message, which on its own explains nothing.
            throw ExtensionException("could not read the archive: ${e.javaClass.simpleName}")
        } finally {
            // The staging directory is never left behind, whatever happened.
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    /** Deletes an installed extension; false when the name is unsafe or absent. */
    fun remove(directory: String): Boolean {
        if (directory.isBlank() || directory == "." || directory.contains('/') || directory.contains("..")) {
            return false
        }
        val target = File(extensionsDir, directory)
        if (!target.exists()) return false
        target.deleteRecursively()
        return !target.exists()
    }

    /**
     * The directory to move into place: the staging root when it holds a
     * manifest.json directly, otherwise the single top-level folder when the
     * archive wraps everything in it.
     */
    private fun findPayload(staging: File): File {
        if (File(staging, "manifest.json").isFile) return staging
        // Archives carry junk beside the wrapper (a README, the __MACOSX
        // directory a Mac zip adds), so the wrapper is found by its manifest
        // rather than by being the only thing in the archive.
        val candidates = staging.listFiles()
            ?.filter { it.isDirectory && it.name != "__MACOSX" && !it.name.startsWith(".") }
            ?.filter { File(it, "manifest.json").isFile }
            ?: emptyList()
        if (candidates.size == 1) return candidates[0]
        throw ExtensionException("no manifest.json in the archive - is this a packed extension?")
    }

    /** Copies the archive into the staging directory, rejecting unsafe archives. */
    private fun extract(input: InputStream, staging: File) {
        val stagingCanonical = staging.canonicalPath
        var entries = 0
        var totalBytes = 0L
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries++
                if (entries > MAX_ENTRIES) {
                    throw ExtensionException("too many files in the archive")
                }
                val name = entry.name
                if (isUnsafePath(name, stagingCanonical)) {
                    throw ExtensionException("unsafe path in the archive: $name")
                }
                val target = File(staging, name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { out ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            totalBytes += read
                            if (totalBytes > MAX_BYTES) {
                                throw ExtensionException("the archive is too large")
                            }
                            out.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    /** Absolute paths, `..` segments and canonical escapes are all rejected. */
    private fun isUnsafePath(name: String, stagingCanonical: String): Boolean {
        if (name.startsWith("/") || name.split('/').contains("..")) return true
        val canonical = runCatching { File(stagingCanonical, name).canonicalPath }.getOrNull() ?: return true
        return !canonical.startsWith(stagingCanonical + File.separator)
    }

    /**
     * Skips a CRX header when present, leaving a plain zip for
     * [ZipInputStream]. Reads through a mark/reset buffer so a plain zip is
     * never consumed.
     */
    private fun InputStream.openZip(): InputStream {
        val buffered = if (this is BufferedInputStream) this else BufferedInputStream(this)
        buffered.mark(MAX_HEADER)
        val magic = ByteArray(MAGIC_LENGTH)
        var read = 0
        while (read < MAGIC_LENGTH) {
            val n = buffered.read(magic, read, MAGIC_LENGTH - read)
            if (n < 0) break
            read += n
        }
        if (read < MAGIC_LENGTH || String(magic, Charsets.US_ASCII) != "Cr24") {
            buffered.reset()
            return buffered
        }
        val version = readLeUInt32(buffered)
        when (version) {
            3L -> buffered.skipFully(readLeUInt32(buffered))
            2L -> {
                val publicKeyLength = readLeUInt32(buffered)
                val signatureLength = readLeUInt32(buffered)
                buffered.skipFully(publicKeyLength + signatureLength)
            }
            else -> throw ExtensionException("unsupported CRX version $version")
        }
        return buffered
    }

    private fun readLeUInt32(input: InputStream): Long {
        val bytes = ByteArray(4)
        var read = 0
        while (read < 4) {
            val n = input.read(bytes, read, 4 - read)
            if (n < 0) break
            read += n
        }
        return (bytes[0].toLong() and 0xFF) or
            ((bytes[1].toLong() and 0xFF) shl 8) or
            ((bytes[2].toLong() and 0xFF) shl 16) or
            ((bytes[3].toLong() and 0xFF) shl 24)
    }

    private fun InputStream.skipFully(n: Long) {
        var remaining = n
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                // skip() can return 0 without reaching EOF; fall back to reading.
                if (read() < 0) return
                remaining--
            }
        }
    }

    /** Sanitised directory name under data/extensions for the picked file. */
    private fun deriveDirName(fileName: String): String {
        var base = fileName
        for (ext in listOf(".zip", ".crx")) {
            if (base.endsWith(ext, ignoreCase = true)) {
                base = base.dropLast(ext.length)
                break
            }
        }
        val cleaned = base.lowercase().map { c ->
            if (c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_') c else '-'
        }.joinToString("")
        val collapsed = cleaned.replace(Regex("-+"), "-").trim('-')
        return collapsed.ifEmpty { "extension" }
    }

    /**
     * Reads the manifest's name and version with a small JSON-string regex.
     * org.json is not functional in local unit tests, and only these two
     * string fields are needed. A `__MSG_` name is a localisation placeholder
     * and falls back to the directory name.
     */
    private fun readMetadata(manifest: File, directory: String): Pair<String, String> {
        val text = runCatching { manifest.readText() }.getOrNull() ?: return directory to ""
        val name = readJsonString(text, "name")
        val version = readJsonString(text, "version")
        val displayName = if (name.isBlank() || name.startsWith("__MSG_")) directory else name
        return displayName to version
    }

    private fun readJsonString(json: String, key: String): String {
        val pattern = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        val match = pattern.find(json) ?: return ""
        return match.groupValues[1]
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private companion object {
        const val STAGING_NAME = ".staging"
        const val MAGIC_LENGTH = 4
        const val MAX_HEADER = 4096
        const val MAX_ENTRIES = 20000
        const val MAX_BYTES = 250L * 1024 * 1024
    }
}