package dev.mrbean.aibrowser.ui

import android.app.Application
import dev.mrbean.aibrowser.engine.InstallState
import dev.mrbean.aibrowser.engine.RootfsInstaller
import dev.mrbean.aibrowser.engine.RootfsUpdates
import dev.mrbean.aibrowser.engine.ServiceState
import dev.mrbean.aibrowser.engine.ServiceSupervisor
import dev.mrbean.aibrowser.service.ServiceController
import kotlinx.coroutines.delay

/**
 * True when the service is up or retrying, so a configuration change must
 * restart it to re-read its files. A [ServiceState.Stopped], [ServiceState.Failed]
 * or [ServiceState.Disabled] service picks the new files up the next time it
 * starts anyway, so nothing needs to be done.
 */
fun decideRestart(state: ServiceState?): Boolean = when (state) {
    is ServiceState.Running, is ServiceState.Starting, is ServiceState.Backoff -> true
    is ServiceState.Stopped, is ServiceState.Failed, is ServiceState.Disabled, null -> false
}

/**
 * Restarts [name] after a configuration change when it is currently running,
 * starting or backing off, so it re-reads files like `data/mcp.host` that are
 * loaded once at start. A stopped or disabled service reads them on its next
 * start, so it is left alone.
 */
internal fun restartService(
    application: Application,
    supervisor: ServiceSupervisor,
    name: String,
) {
    if (decideRestart(supervisor.statuses.value[name]?.state)) {
        ServiceController.start(application, ServiceController.ACTION_RESTART, name)
    }
}

/** True when any service is up, starting or retrying. */
internal fun servicesActive(supervisor: ServiceSupervisor): Boolean =
    supervisor.statuses.value.values.any { decideRestart(it.state) }

/** Waits up to 30 seconds for every service to leave Running/Starting/Backoff. */
internal suspend fun waitForServicesStopped(supervisor: ServiceSupervisor): Boolean {
    val deadline = System.currentTimeMillis() + STOP_WAIT_MS
    while (servicesActive(supervisor)) {
        if (System.currentTimeMillis() >= deadline) return false
        delay(POLL_INTERVAL_MS)
    }
    return true
}

/**
 * Sends [ServiceController.ACTION_STOP_ALL] and waits (up to 30 s) for the
 * supervisor to report no service in Running/Starting/Backoff. Returns false on
 * a timeout, so the caller can report a failed state instead of proceeding.
 */
internal suspend fun stopAllAndWait(
    application: Application,
    supervisor: ServiceSupervisor,
): Boolean {
    ServiceController.start(application, ServiceController.ACTION_STOP_ALL)
    return waitForServicesStopped(supervisor)
}

/**
 * Replaces the userland safely: stops every service through the
 * [ServiceController], waits for the supervisor to report them stopped (30 s
 * cap), installs the manifest's tarball, then starts everything again only when
 * the install actually succeeded. Progress is observed through
 * [RootfsInstaller.state]. Cancelling the calling job aborts the install and
 * skips the restart.
 */
internal suspend fun updateRootfs(
    application: Application,
    supervisor: ServiceSupervisor,
    installer: RootfsInstaller,
    updates: RootfsUpdates,
    manifestUrl: String,
): InstallState {
    if (!stopAllAndWait(application, supervisor)) {
        return InstallState.Failed("timed out stopping the services after 30 seconds")
    }
    installer.install(manifestUrl)
    val result = installer.state.value
    if (result is InstallState.Installed) {
        updates.markInstalled(result.version)
        ServiceController.start(application, ServiceController.ACTION_START_ALL)
    }
    return result
}

private const val STOP_WAIT_MS = 30_000L
private const val POLL_INTERVAL_MS = 200L