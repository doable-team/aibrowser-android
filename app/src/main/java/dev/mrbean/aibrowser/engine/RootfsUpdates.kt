package dev.mrbean.aibrowser.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** The result of the last rootfs update check, shown on the Settings and Repair pages. */
sealed interface UpdateStatus {
    data object Unknown : UpdateStatus
    data class UpToDate(val version: String) : UpdateStatus
    data class Available(val version: String, val sizeBytes: Long) : UpdateStatus
    data class Failed(val message: String) : UpdateStatus
}

/**
 * Checks the release manifest for a newer rootfs without touching the installed
 * one: it downloads only the (small) manifest into `data/rootfs-manifest-check.json`
 * and compares its version with [AppConfig.rootfsVersion]. Neither the installed
 * rootfs nor the config are ever written.
 */
class RootfsUpdates(
    private val paths: Paths,
    private val downloader: Downloader,
    private val config: ConfigStore,
) {

    private companion object {
        const val MANIFEST_FILE = "rootfs-manifest-check.json"
        const val MESSAGE_LIMIT = 120
    }

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Unknown)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    suspend fun check(manifestUrl: String) = withContext(Dispatchers.IO) {
        _status.value = UpdateStatus.Unknown
        try {
            val manifestFile = File(paths.data, MANIFEST_FILE)
            downloader.download(manifestUrl, manifestFile)
            val manifest = RootfsManifest.parse(manifestFile.readText())
            val installed = config.load().rootfsVersion
            _status.value = if (installed.isEmpty() || installed == manifest.version) {
                UpdateStatus.UpToDate(manifest.version)
            } else {
                UpdateStatus.Available(manifest.version, manifest.size)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _status.value = UpdateStatus.Failed(shortMessage(e))
        }
    }

    /**
     * Records a version that was just installed, so the Settings and Repair
     * lines stop offering an update that has already been applied without
     * waiting for another manifest download.
     */
    fun markInstalled(version: String) {
        _status.value = UpdateStatus.UpToDate(version)
    }

    private fun shortMessage(e: Exception): String = when (e) {
        is DownloadException -> "download failed (HTTP ${e.status})"
        else -> (e.message ?: e.javaClass.simpleName).take(MESSAGE_LIMIT)
    }
}