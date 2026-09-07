package dev.mrbean.aibrowser.ui

import android.app.Application
import dev.mrbean.aibrowser.engine.ServiceState
import dev.mrbean.aibrowser.engine.ServiceSupervisor
import dev.mrbean.aibrowser.service.ServiceController

/**
 * True when the service is up or retrying, so a configuration change must
 * restart it to re-read its files. A [ServiceState.Stopped] or
 * [ServiceState.Disabled] service picks the new files up the next time it
 * starts anyway, so nothing needs to be done.
 */
fun decideRestart(state: ServiceState?): Boolean = when (state) {
    is ServiceState.Running, is ServiceState.Starting, is ServiceState.Backoff -> true
    is ServiceState.Stopped, is ServiceState.Disabled, null -> false
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