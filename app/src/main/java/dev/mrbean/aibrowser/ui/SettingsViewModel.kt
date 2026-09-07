package dev.mrbean.aibrowser.ui

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.mrbean.aibrowser.AiBrowserApp
import dev.mrbean.aibrowser.AppGraph
import dev.mrbean.aibrowser.engine.ApiToken
import dev.mrbean.aibrowser.engine.ChromiumFlags
import dev.mrbean.aibrowser.engine.ExtensionInstaller
import dev.mrbean.aibrowser.engine.InstallState
import dev.mrbean.aibrowser.engine.InstalledExtension
import dev.mrbean.aibrowser.engine.SecretFile
import dev.mrbean.aibrowser.engine.ServiceState
import dev.mrbean.aibrowser.engine.TokenStore
import dev.mrbean.aibrowser.engine.UpdateStatus
import dev.mrbean.aibrowser.service.ServiceController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

data class SettingsUiState(
    val tunnelToken: String = "",
    val showTunnelToken: Boolean = false,
    val tokens: List<ApiToken> = emptyList(),
    val addLabel: String = "",
    val mcpHost: String = "",
    val statusHost: String = "",
    val viewerHost: String = "",
    val chromiumFlags: String = "",
    val extensions: List<InstalledExtension> = emptyList(),
    val startOnBoot: Boolean = false,
    val rootfsVersion: String = "",
    val manifestUrl: String = "",
    val rootfsBusy: Boolean = false,
    val extensionBusy: Boolean = false,
)

class SettingsViewModel(graph: AppGraph, application: Application) : AndroidViewModel(application) {

    private val paths = graph.paths
    private val config = graph.config
    private val installer = graph.installer
    private val supervisor = graph.supervisor
    private val updates = graph.updates
    private val tokenStore = TokenStore(paths.data)
    private val tunnelFile = SecretFile(File(paths.data, "tunnel.token"))
    private val mcpHostFile = SecretFile(File(paths.data, "mcp.host"))
    private val chromiumFlagsFile = File(paths.data, "chromium.flags")
    private val extensionInstaller = ExtensionInstaller(paths)

    val appVersionName: String = versionName(application)

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    val updateStatus: StateFlow<UpdateStatus> = updates.status

    private val _installState = MutableStateFlow<InstallState>(installer.state.value)
    val installState: StateFlow<InstallState> = _installState.asStateFlow()

    /** The job driving the current rootfs update or uninstall; null when idle. */
    private var updateJob: Job? = null

    /** Incremented after every successful write; the screen shows a snackbar. */
    private val _savedCount = MutableStateFlow(0)
    val savedCount: StateFlow<Int> = _savedCount.asStateFlow()

    /** The text of the snackbar shown after the last successful write. */
    private val _savedMessage = MutableStateFlow("Saved")
    val savedMessage: StateFlow<String> = _savedMessage.asStateFlow()

    /** The just-added API token, shown once in a dialog. */
    private val _pendingToken = MutableStateFlow<ApiToken?>(null)
    val pendingToken: StateFlow<ApiToken?> = _pendingToken.asStateFlow()

    init {
        viewModelScope.launch {
            installer.state.collect { _installState.value = it }
        }
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val cfg = config.load()
                ChromiumFlags.ensure(paths.data)
                SettingsUiState(
                    tunnelToken = tunnelFile.readOrEmpty(),
                    tokens = tokenStore.list(),
                    mcpHost = cfg.mcpHost,
                    statusHost = cfg.statusHost,
                    viewerHost = cfg.viewerHost,
                    chromiumFlags = chromiumFlagsFile.readText().trim(),
                    extensions = extensionInstaller.list(),
                    startOnBoot = cfg.startOnBoot,
                    rootfsVersion = cfg.rootfsVersion,
                    manifestUrl = cfg.mirrorUrl,
                )
            }
            _state.value = loaded
        }
    }

    // Tunnel

    fun onTunnelTokenChange(value: String) {
        _state.update { it.copy(tunnelToken = value) }
    }

    // Startup

    fun setStartOnBoot(value: Boolean) {
        _state.update { it.copy(startOnBoot = value) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                config.save(config.load().copy(startOnBoot = value))
            }
        }
    }

    fun toggleShowTunnelToken() {
        _state.update { it.copy(showTunnelToken = !it.showTunnelToken) }
    }

    fun saveTunnel() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { tunnelFile.write(_state.value.tunnelToken) }
            val tunnelState = supervisor.statuses.value["tunnel"]?.state
            when (tunnelState) {
                is ServiceState.Disabled ->
                    ServiceController.start(getApplication(), ServiceController.ACTION_START, "tunnel")
                else -> restartService(getApplication(), supervisor, "tunnel")
            }
            _savedMessage.value = "Saved"
            _savedCount.update { it + 1 }
        }
    }

    fun clearTunnel() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { tunnelFile.write("") }
            _state.update { it.copy(tunnelToken = "") }
            _savedMessage.value = "Saved"
            _savedCount.update { it + 1 }
        }
    }

    // API tokens

    fun onAddLabelChange(value: String) {
        _state.update { it.copy(addLabel = value) }
    }

    fun addToken() {
        val label = _state.value.addLabel
        if (label.isBlank()) return
        viewModelScope.launch {
            val (added, tokens) = withContext(Dispatchers.IO) {
                val created = tokenStore.add(label)
                created to tokenStore.list()
            }
            _state.update { it.copy(addLabel = "", tokens = tokens) }
            _pendingToken.value = added
        }
    }

    fun removeToken(token: String) {
        viewModelScope.launch {
            val tokens = withContext(Dispatchers.IO) {
                tokenStore.remove(token)
                tokenStore.list()
            }
            _state.update { it.copy(tokens = tokens) }
            _savedMessage.value = "Saved"
            _savedCount.update { it + 1 }
        }
    }

    fun dismissPendingToken() {
        _pendingToken.value = null
    }

    // Hostnames

    fun onMcpHostChange(value: String) {
        _state.update { it.copy(mcpHost = value) }
    }

    fun onStatusHostChange(value: String) {
        _state.update { it.copy(statusHost = value) }
    }

    fun onViewerHostChange(value: String) {
        _state.update { it.copy(viewerHost = value) }
    }

    fun saveHostnames() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                mcpHostFile.write(_state.value.mcpHost)
                config.save(
                    config.load().copy(
                        mcpHost = _state.value.mcpHost,
                        statusHost = _state.value.statusHost,
                        viewerHost = _state.value.viewerHost,
                    ),
                )
            }
            restartService(getApplication(), supervisor, "mcp")
            restartService(getApplication(), supervisor, "tunnel")
            _savedMessage.value = "Saved; restarting the MCP server"
            _savedCount.update { it + 1 }
        }
    }

    // Chromium

    fun onChromiumFlagsChange(value: String) {
        _state.update { it.copy(chromiumFlags = value) }
    }

    fun saveChromiumFlags() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { writeAtomic(chromiumFlagsFile, _state.value.chromiumFlags.trim()) }
            _savedMessage.value = "Saved"
            _savedCount.update { it + 1 }
        }
    }

    fun restartChromium() {
        ServiceController.start(getApplication(), ServiceController.ACTION_RESTART, "chromium")
    }

    // Extensions

    fun installExtension(fileName: String, open: () -> InputStream?) {
        if (_state.value.extensionBusy) return
        _state.update { it.copy(extensionBusy = true) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val input = open() ?: return@withContext null
                    val done = input.use { extensionInstaller.install(fileName, it) }
                    done to extensionInstaller.list()
                }
                if (result == null) {
                    _savedMessage.value = "could not read the picked file"
                } else {
                    val (installed, extensions) = result
                    _state.update { it.copy(extensions = extensions) }
                    _savedMessage.value = "Installed ${installed.name} - restart chromium to load it"
                }
                _savedCount.update { it + 1 }
            } catch (e: Exception) {
                _savedMessage.value = e.message ?: "could not install the extension"
                _savedCount.update { it + 1 }
            } finally {
                _state.update { it.copy(extensionBusy = false) }
            }
        }
    }

    fun removeExtension(directory: String) {
        if (_state.value.extensionBusy) return
        _state.update { it.copy(extensionBusy = true) }
        viewModelScope.launch {
            try {
                val (removed, extensions) = withContext(Dispatchers.IO) {
                    val gone = extensionInstaller.remove(directory)
                    gone to extensionInstaller.list()
                }
                _state.update { it.copy(extensions = extensions) }
                _savedMessage.value = if (removed) {
                    "Removed $directory - restart chromium"
                } else {
                    "could not remove $directory"
                }
                _savedCount.update { it + 1 }
            } catch (e: Exception) {
                _savedMessage.value = e.message ?: "could not remove $directory"
                _savedCount.update { it + 1 }
            } finally {
                _state.update { it.copy(extensionBusy = false) }
            }
        }
    }

    // Rootfs

    private fun manifestUrl(): String = _state.value.manifestUrl.ifBlank { DEFAULT_MANIFEST_URL }

    fun checkUpdates() {
        viewModelScope.launch {
            updates.check(manifestUrl())
        }
    }

    fun uninstallRootfs() {
        if (updateJob?.isActive == true) return
        _state.update { it.copy(rootfsBusy = true) }
        updateJob = viewModelScope.launch {
            try {
                val result = if (stopAllAndWait(getApplication(), supervisor)) {
                    installer.uninstall()
                    installer.state.value
                } else {
                    InstallState.Failed("timed out stopping the services after 30 seconds")
                }
                _installState.value = result
                refreshInstalledVersion()
            } finally {
                updateJob = null
                _state.update { it.copy(rootfsBusy = false) }
            }
        }
    }

    private fun refreshInstalledVersion() {
        viewModelScope.launch {
            val version = withContext(Dispatchers.IO) { config.load().rootfsVersion }
            _state.update { it.copy(rootfsVersion = version) }
        }
    }

    private fun writeAtomic(file: File, content: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(content)
        // renameTo is atomic on the same filesystem; copy as a fallback.
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }

    private fun versionName(application: Application): String =
        if (Build.VERSION.SDK_INT >= 33) {
            application.packageManager
                .getPackageInfo(application.packageName, PackageManager.PackageInfoFlags.of(0))
                .versionName.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            application.packageManager.getPackageInfo(application.packageName, 0).versionName.orEmpty()
        }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AiBrowserApp
                SettingsViewModel(app.graph, app)
            }
        }
    }
}