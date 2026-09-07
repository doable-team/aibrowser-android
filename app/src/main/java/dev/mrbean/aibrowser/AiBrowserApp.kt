package dev.mrbean.aibrowser

import android.app.Application
import android.content.Context
import dev.mrbean.aibrowser.engine.ConfigStore
import dev.mrbean.aibrowser.engine.Downloader
import dev.mrbean.aibrowser.engine.NativeBinaries
import dev.mrbean.aibrowser.engine.Paths
import dev.mrbean.aibrowser.engine.PhantomProcessGuard
import dev.mrbean.aibrowser.engine.ProcessRunner
import dev.mrbean.aibrowser.engine.RootfsInstaller
import dev.mrbean.aibrowser.engine.RootfsUpdates
import dev.mrbean.aibrowser.engine.ServiceSupervisor
import dev.mrbean.aibrowser.ui.DEFAULT_MANIFEST_URL
import dev.mrbean.aibrowser.ui.servicesActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Owns the [AppGraph]. The service supervisor outlives any single screen or the
 * foreground service, so its coroutine scope lives here and is only torn down
 * when the process dies.
 */
class AiBrowserApp : Application() {

    val graph: AppGraph by lazy {
        val paths = Paths.from(this)
        // Android moves the native library directory on every install; the
        // bin/ and lib/ symlinks are recreated whenever the process starts.
        NativeBinaries.prepare(paths)
        // Keep Android from killing the service processes when their number
        // passes the phantom-process limit; a no-op without the permission.
        PhantomProcessGuard.ensureDisabled(this)
        val runner = ProcessRunner()
        val config = ConfigStore(paths.data)
        val downloader = Downloader()
        val supervisorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val supervisor = ServiceSupervisor(paths, runner, supervisorScope)
        val graph = AppGraph(
            paths = paths,
            runner = runner,
            config = config,
            supervisorScope = supervisorScope,
            supervisor = supervisor,
            installer = RootfsInstaller(
                paths, runner, downloader, config,
                // The userland must not be wiped out from under the services.
                servicesRunning = { servicesActive(supervisor) },
            ),
            downloader = downloader,
            updates = RootfsUpdates(paths, downloader, config),
        )
        // Give the operator an update answer soon after start; a failed check
        // is retried from the Settings screen.
        supervisorScope.launch {
            runCatching {
                val cfg = graph.config.load()
                graph.updates.check(cfg.mirrorUrl.ifBlank { DEFAULT_MANIFEST_URL })
            }
        }
        graph
    }

    companion object {
        fun graphOf(context: Context): AppGraph = (context.applicationContext as AiBrowserApp).graph
    }
}