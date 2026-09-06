package dev.mrbean.aibrowser.engine

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

data class PrepareReport(
    val linksCreated: List<Pair<String, String>>,
    val missingNativeFiles: List<String>,
    val errors: List<String> = emptyList(),
)

/**
 * Creates the symlinks the proot stack needs under [Paths.bin] and [Paths.lib],
 * pointing at the extracted native libraries in [Paths.nativeLibraryDir].
 *
 * Missing native libraries are reported in [PrepareReport.missingNativeFiles] and
 * their links are skipped; this never throws because of a missing file.
 */
object NativeBinaries {

    // link path (relative to paths.root) -> file in nativeLibraryDir
    private val LINKS = listOf(
        "bin/proot" to "libexec_proot.so",
        "bin/busybox" to "libexec_busybox.so",
        "bin/sh" to "libexec_busybox.so",
        "bin/cat" to "libexec_busybox.so",
        "bin/xz" to "libexec_busybox.so",
        "bin/gzip" to "libexec_busybox.so",
        "bin/tar" to "libexec_tar.so",
        "lib/libtalloc.so.2" to "libtalloc.so",
        "lib/loader" to "libproot-loader.so",
        "lib/libacl.so" to "libacl.so",
        "lib/libandroid-selinux.so" to "libandroid-selinux.so",
        "lib/libiconv.so" to "libiconv.so",
        "lib/libcharset.so" to "libcharset.so",
        "lib/libattr.so" to "libattr.so",
        "lib/libpcre2-8.so" to "libpcre2-8.so",
        "lib/libbusybox.so.1.37.0" to "libbusybox.so",
    )

    fun prepare(paths: Paths): PrepareReport {
        paths.ensureDirs()
        val missing = LINKS.map { it.second }.distinct()
            .filter { !File(paths.nativeLibraryDir, it).isFile }

        val created = mutableListOf<Pair<String, String>>()
        val errors = mutableListOf<String>()
        for ((linkPath, targetName) in LINKS) {
            if (targetName in missing) continue
            try {
                val link = Path.of(paths.root.absolutePath, linkPath)
                val target = Path.of(paths.nativeLibraryDir, targetName)
                Files.deleteIfExists(link)
                Files.createSymbolicLink(link, target)
                created += linkPath to targetName
            } catch (e: Exception) {
                errors += "$linkPath: ${e.message ?: e.javaClass.simpleName}"
            }
        }

        setTmpPermissions(paths.tmp)
        return PrepareReport(created, missing, errors)
    }

    // proot's /dev/shm needs to be world-writable and sticky; best effort only.
    private fun setTmpPermissions(tmp: File) {
        if (!tmp.exists()) return
        runCatching {
            Files.setPosixFilePermissions(tmp.toPath(), PosixFilePermissions.fromString("rwxrwxrwx"))
            Files.setAttribute(tmp.toPath(), "unix:mode", 0x3FF.toInt()) // 01777
        }
    }
}