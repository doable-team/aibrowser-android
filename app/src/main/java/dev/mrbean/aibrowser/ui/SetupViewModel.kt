package dev.mrbean.aibrowser.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mrbean.aibrowser.engine.ConfigStore
import dev.mrbean.aibrowser.engine.Downloader
import dev.mrbean.aibrowser.engine.InstallState
import dev.mrbean.aibrowser.engine.NativeBinaries
import dev.mrbean.aibrowser.engine.Paths
import dev.mrbean.aibrowser.engine.ProcessRunner
import dev.mrbean.aibrowser.engine.RootfsInstaller
import dev.mrbean.aibrowser.engine.SelfTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEFAULT_MANIFEST_URL = "https://mrbean.dev/aibrowser/manifest.json"

data class SetupUiState(
    val busy: Boolean = false,
    val report: String = "",
    val manifestUrl: String = DEFAULT_MANIFEST_URL,
    val installState: InstallState = InstallState.Idle,
    val rootfsBusy: Boolean = false,
)

class SetupViewModel(application: Application) : AndroidViewModel(application) {
    private val paths = Paths.from(application)
    private val runner = ProcessRunner()
    private val selfTest = SelfTest(paths, runner)
    private val installer = RootfsInstaller(paths, runner, Downloader(), ConfigStore(paths.data))

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

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

    fun onManifestUrlChange(value: String) {
        _state.update { it.copy(manifestUrl = value) }
    }

    fun install() {
        if (installJob?.isActive == true) return
        _state.update { it.copy(rootfsBusy = true) }
        installJob = viewModelScope.launch {
            try {
                installer.install(_state.value.manifestUrl)
            } finally {
                installJob = null
                _state.update { it.copy(rootfsBusy = false) }
            }
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
                installer.uninstall()
            } finally {
                installJob = null
                _state.update { it.copy(rootfsBusy = false) }
            }
        }
    }
}