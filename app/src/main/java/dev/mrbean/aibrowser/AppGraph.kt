package dev.mrbean.aibrowser

import dev.mrbean.aibrowser.engine.ConfigStore
import dev.mrbean.aibrowser.engine.Downloader
import dev.mrbean.aibrowser.engine.Paths
import dev.mrbean.aibrowser.engine.ProcessRunner
import dev.mrbean.aibrowser.engine.RootfsInstaller
import dev.mrbean.aibrowser.engine.ServiceSupervisor
import kotlinx.coroutines.CoroutineScope

/**
 * Composition root of the engine: everything a screen or the foreground
 * service needs, built once in [AiBrowserApp].
 */
class AppGraph(
    val paths: Paths,
    val runner: ProcessRunner,
    val config: ConfigStore,
    val supervisorScope: CoroutineScope,
    val supervisor: ServiceSupervisor,
    val installer: RootfsInstaller,
    val downloader: Downloader,
)