package dev.mrbean.aibrowser.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.atomic.AtomicReference

/** Progress of a rootfs install/uninstall, exposed through [RootfsInstaller.state]. */
sealed interface InstallState {
    data object Idle : InstallState
    data object FetchingManifest : InstallState
    data class Downloading(val done: Long, val total: Long, val bytesPerSecond: Long) : InstallState
    data class Verifying(val done: Long, val total: Long) : InstallState
    data class Extracting(val elapsedSec: Int, val lastPath: String) : InstallState
    data object WritingFiles : InstallState
    data class Installed(val version: String) : InstallState
    data class Failed(val message: String) : InstallState
}

/** Signals a controlled install failure whose [message] is user-presentable. */
class InstallException(message: String) : Exception(message)

/**
 * Downloads, verifies and extracts the Debian rootfs tarball, then writes the
 * few files a `docker export` cannot carry and records the installed version in
 * [AppConfig].
 *
 * The extraction itself runs on the HOST (no rootfs mounted): `proot
 * --link2symlink -0 bin/tar x -J --delay-directory-restore
 * --preserve-permissions -v -f <tarball> -C <rootfs>` with the proot env, which
 * puts `bin` on PATH so tar can find the busybox xz. Hard links and device-like
 * paths in the export are handled by proot's `--link2symlink`.
 */
class RootfsInstaller(
    private val paths: Paths,
    private val runner: ProcessRunner,
    private val downloader: Downloader,
    private val config: ConfigStore,
) {

    private companion object {
        const val MANIFEST_FILE = "rootfs-manifest.json"
        const val EXTRACT_TIMEOUT_MS = 30 * 60 * 1000L
        const val TICKER_INTERVAL_MS = 1_000L

        val RESOLV_CONF = "nameserver 1.1.1.1\nnameserver 8.8.8.8\n"
        val HOSTS = "127.0.0.1 localhost\n::1 localhost\n"
    }

    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state.asStateFlow()

    @Volatile
    private var currentTarball: File? = null

    init {
        if (isInstalled()) {
            _state.value = InstallState.Installed(config.load().rootfsVersion)
        }
    }

    fun isInstalled(): Boolean =
        File(paths.rootfs, "usr/bin/env").exists() && config.load().rootfsVersion.isNotEmpty()

    suspend fun install(manifestUrl: String) = withContext(Dispatchers.IO) {
        try {
            // Android moves the native library directory on every install, so
            // the bin/ and lib/ symlinks are refreshed before proot is needed.
            NativeBinaries.prepare(paths)
            paths.data.mkdirs()
            paths.rootfs.mkdirs()

            _state.value = InstallState.FetchingManifest
            val manifestFile = File(paths.data, MANIFEST_FILE)
            downloader.download(manifestUrl, manifestFile)
            val manifest = RootfsManifest.parse(manifestFile.readText())

            val tarball = File(paths.data, manifest.file)
            currentTarball = tarball
            downloader.download(manifest.tarballUrl(manifestUrl), tarball) { done, total, bps ->
                _state.value = InstallState.Downloading(done, total, bps)
            }

            _state.value = InstallState.Verifying(0, manifest.size)
            val actualSize = tarball.length()
            if (actualSize != manifest.size) {
                deleteTarball(tarball)
                throw InstallException("size mismatch: expected ${manifest.size} bytes, got $actualSize")
            }
            val actualSha = Sha256.hex(tarball) { done ->
                _state.value = InstallState.Verifying(done, manifest.size)
            }
            if (!actualSha.equals(manifest.sha256, ignoreCase = true)) {
                deleteTarball(tarball)
                throw InstallException("checksum mismatch: expected ${manifest.sha256}, got $actualSha")
            }

            deleteTreeNoFollow(paths.rootfs)
            paths.rootfs.mkdirs()

            extract(manifest, tarball)

            _state.value = InstallState.WritingFiles
            File(paths.rootfs, "etc").mkdirs()
            File(paths.rootfs, "etc/resolv.conf").writeText(RESOLV_CONF)
            File(paths.rootfs, "etc/hosts").writeText(HOSTS)
            val home = File(paths.rootfs, "root")
            File(home, "profile").mkdirs()
            File(home, "logs").mkdirs()

            config.save(config.load().copy(rootfsVersion = manifest.version, mirrorUrl = manifestUrl))
            deleteTarball(tarball)
            _state.value = InstallState.Installed(manifest.version)
        } catch (e: CancellationException) {
            // The .part file stays behind so a later attempt can resume.
            _state.value = if (isInstalled()) {
                InstallState.Installed(config.load().rootfsVersion)
            } else {
                InstallState.Idle
            }
            throw e
        } catch (e: Exception) {
            _state.value = InstallState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    suspend fun uninstall() = withContext(Dispatchers.IO) {
        try {
            deleteTreeNoFollow(paths.rootfs)
            currentTarball?.let { deleteTarball(it) }
            currentTarball = null
            config.save(config.load().copy(rootfsVersion = ""))
            _state.value = InstallState.Idle
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = InstallState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun extract(manifest: RootfsManifest, tarball: File) {
        val spec = ProcessSpec(
            argv = listOf(
                File(paths.bin, "proot").absolutePath,
                "--link2symlink",
                "-0",
                File(paths.bin, "tar").absolutePath,
                "x",
                "-J",
                "--delay-directory-restore",
                "--preserve-permissions",
                "-v",
                "-f",
                tarball.absolutePath,
                "-C",
                paths.rootfs.absolutePath,
            ),
            env = prootEnv(paths),
        )

        val startNanos = System.nanoTime()
        val lastPath = AtomicReference("")
        val stderr = StringBuilder()

        _state.value = InstallState.Extracting(0, "")
        coroutineScope {
            val ticker = launch {
                while (isActive) {
                    delay(TICKER_INTERVAL_MS)
                    val elapsedSec = ((System.nanoTime() - startNanos) / 1_000_000_000L).toInt()
                    _state.value = InstallState.Extracting(elapsedSec, lastPath.get())
                }
            }
            try {
                val result = runner.run(spec, cwd = paths.root, timeoutMs = EXTRACT_TIMEOUT_MS) { line, isStderr ->
                    if (isStderr) {
                        synchronized(stderr) { stderr.append(line).append('\n') }
                    } else {
                        lastPath.set(line)
                    }
                }
                if (result.exitCode != 0) {
                    val detail = synchronized(stderr) { stderr.toString().trim() }
                    val timeout = if (result.timedOut) " (timed out after 30 minutes)" else ""
                    val suffix = if (detail.isEmpty()) "" else ": $detail"
                    throw InstallException("extraction failed (exit ${result.exitCode})$timeout$suffix")
                }
            } finally {
                ticker.cancel()
            }
        }
    }

    private fun deleteTarball(tarball: File) {
        tarball.delete()
        File(tarball.path + ".part").delete()
    }

    /**
     * Deletes a directory tree without following any symlink: symlinks are
     * visited as files and removed, never descended into. Uses
     * [Files.walkFileTree] (which never follows links) instead of `Files.walk`.
     */
    private fun deleteTreeNoFollow(dir: File) {
        if (!dir.exists()) return
        Files.walkFileTree(
            dir.toPath(),
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    Files.deleteIfExists(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }
}