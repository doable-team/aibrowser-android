package dev.mrbean.aibrowser.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.mrbean.aibrowser.AiBrowserApp
import dev.mrbean.aibrowser.AppGraph
import dev.mrbean.aibrowser.engine.ConfigStore
import dev.mrbean.aibrowser.engine.InstallState
import dev.mrbean.aibrowser.engine.NativeBinaries
import dev.mrbean.aibrowser.engine.SelfTest
import dev.mrbean.aibrowser.engine.UpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val DEFAULT_MANIFEST_URL = "https://mrbean.dev/aibrowser/manifest.json"

data class SetupUiState(
    val busy: Boolean = false,
    val report: String = "",
    val manifestUrl: String = DEFAULT_MANIFEST_URL,
    val installState: InstallState = InstallState.Idle,
    val rootfsBusy: Boolean = false,
)

class SetupViewModel(graph: AppGraph, application: Application) : AndroidViewModel(application) {
    private val paths = graph.paths
    private val selfTest = SelfTest(paths, graph.runner)
    private val installer = graph.installer
    private val supervisor = graph.supervisor
    private val updates = graph.updates

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    val updateStatus: StateFlow<UpdateStatus> = updates.status

    private var installJob: Job? = null

    init {
        val loaded = ConfigStore(paths.data).load()
        _state.value = SetupUiState(
            manifestUrl = loaded.mirrorUrl.ifBlank { DEFAULT_MANIFEST_URL },
            installState = installer.state.value,
        )
        viewModelScope.launch {
            installer.state.collect { installState ->
                _state.update { it.copy(installState = installState) }
            }
        }
    }

    fun prepare() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, report = "Preparing native binaries…") }
            val report = withContext(Dispatchers.IO) { NativeBinaries.prepare(paths) }
            _state.update {
                it.copy(
                    busy = false,
                    report = buildString {
                        appendLine("Prepared ${report.linksCreated.size} symlinks:")
                        report.linksCreated.forEach { (link, target) -> appendLine("  $link -> $target") }
                        if (report.missingNativeFiles.isNotEmpty()) {
                            appendLine("MISSING native files: ${report.missingNativeFiles.joinToString()}")
                        }
                        if (report.errors.isNotEmpty()) {
                            appendLine("ERRORS:")
                            report.errors.forEach { appendLine("  $it") }
                        }
                    },
                )
            }
        }
    }

    fun runSelfTest() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, report = "Running self-test…") }
            val report = withContext(Dispatchers.IO) { selfTest.run() }
            _state.update {
                it.copy(
                    busy = false,
                    report = buildString {
                        appendLine("proot --version (exit ${report.proot.exitCode}):")
                        append(report.proot.stdout)
                        append(report.proot.stderr)
                        appendLine("busybox echo ok (exit ${report.busybox.exitCode}):")
                        append(report.busybox.stdout)
                        append(report.busybox.stderr)
                        if (report.proot.timedOut || report.busybox.timedOut) appendLine("(timed out)")
                    },
                )
            }
        }
    }

    /** Back to the built-in manifest URL; also forgets the remembered mirror. */
    fun resetManifestUrl() {
        _state.update { it.copy(manifestUrl = DEFAULT_MANIFEST_URL) }
        viewModelScope.launch(Dispatchers.IO) {
            val store = ConfigStore(paths.data)
            store.save(store.load().copy(mirrorUrl = ""))
        }
    }

    fun onManifestUrlChange(value: String) {
        _state.update { it.copy(manifestUrl = value) }
    }

    fun install() {
        if (installJob?.isActive == true) return
        _state.update { it.copy(rootfsBusy = true) }
        installJob = viewModelScope.launch {
            try {
                // Reinstalling while the services run out of the userland would
                // wipe it from under them, so the shared action stops and waits
                // first and only restarts everything after a successful install.
                val result = updateRootfs(getApplication(), supervisor, installer, updates, _state.value.manifestUrl)
                _state.update { it.copy(installState = result) }
            } finally {
                installJob = null
                _state.update { it.copy(rootfsBusy = false) }
            }
        }
    }

    fun checkUpdates() {
        viewModelScope.launch {
            updates.check(_state.value.manifestUrl)
        }
    }

    fun cancelInstall() {
        installJob?.cancel()
    }

    fun uninstall() {
        if (installJob?.isActive == true) return
        _state.update { it.copy(rootfsBusy = true) }
        installJob = viewModelScope.launch {
            try {
                val result = if (stopAllAndWait(getApplication(), supervisor)) {
                    installer.uninstall()
                    installer.state.value
                } else {
                    InstallState.Failed("timed out stopping the services after 30 seconds")
                }
                _state.update { it.copy(installState = result) }
            } finally {
                installJob = null
                _state.update { it.copy(rootfsBusy = false) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AiBrowserApp
                SetupViewModel(app.graph, app)
            }
        }
    }
}