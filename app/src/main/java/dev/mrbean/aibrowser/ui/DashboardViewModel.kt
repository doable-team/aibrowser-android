package dev.mrbean.aibrowser.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.mrbean.aibrowser.AiBrowserApp
import dev.mrbean.aibrowser.AppGraph
import dev.mrbean.aibrowser.engine.InstallState
import dev.mrbean.aibrowser.engine.ServiceStatus
import dev.mrbean.aibrowser.service.ServiceController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dashboard state and actions. All start/stop actions are routed through the
 * foreground service via [ServiceController], so the supervisor runs even when
 * no screen is open.
 */
class DashboardViewModel(graph: AppGraph, application: Application) : AndroidViewModel(application) {

    private val supervisor = graph.supervisor

    val statuses: StateFlow<Map<String, ServiceStatus>> = supervisor.statuses

    val rootfsInstalled: StateFlow<Boolean> = graph.installer.state
        .map { it is InstallState.Installed }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            graph.installer.state.value is InstallState.Installed,
        )

    fun startAll() = ServiceController.start(getApplication(), ServiceController.ACTION_START_ALL)
    fun stopAll() = ServiceController.start(getApplication(), ServiceController.ACTION_STOP_ALL)
    fun restartAll() = ServiceController.start(getApplication(), ServiceController.ACTION_RESTART_ALL)
    fun start(name: String) = ServiceController.start(getApplication(), ServiceController.ACTION_START, name)
    fun stop(name: String) = ServiceController.start(getApplication(), ServiceController.ACTION_STOP, name)
    fun restart(name: String) = ServiceController.start(getApplication(), ServiceController.ACTION_RESTART, name)

    fun logLines(name: String): List<String> = supervisor.logLines(name)

    fun clearLog(name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { supervisor.clearLog(name) }
        }
    }

    /** True when the rootfs marker exists, so service processes can actually run. */
    fun rootfsReady(): Boolean = supervisor.rootfsReady()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AiBrowserApp
                DashboardViewModel(app.graph, app)
            }
        }
    }
}