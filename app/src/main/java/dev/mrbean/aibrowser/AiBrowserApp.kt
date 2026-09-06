package dev.mrbean.aibrowser

import android.app.Application
import android.content.Context
import dev.mrbean.aibrowser.engine.ConfigStore
import dev.mrbean.aibrowser.engine.Downloader
import dev.mrbean.aibrowser.engine.NativeBinaries
import dev.mrbean.aibrowser.engine.Paths
import dev.mrbean.aibrowser.engine.ProcessRunner
import dev.mrbean.aibrowser.engine.RootfsInstaller
import dev.mrbean.aibrowser.engine.ServiceSupervisor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
        val runner = ProcessRunner()
        val config = ConfigStore(paths.data)
        val downloader = Downloader()
        val supervisorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        AppGraph(
            paths = paths,
            runner = runner,
            config = config,
            supervisorScope = supervisorScope,
            supervisor = ServiceSupervisor(paths, runner, supervisorScope),
            installer = RootfsInstaller(paths, runner, downloader, config),
            downloader = downloader,
        )
    }

    companion object {
        fun graphOf(context: Context): AppGraph = (context.applicationContext as AiBrowserApp).graph
    }
}