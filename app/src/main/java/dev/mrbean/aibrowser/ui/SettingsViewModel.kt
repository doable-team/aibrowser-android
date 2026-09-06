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
import dev.mrbean.aibrowser.engine.SecretFile
import dev.mrbean.aibrowser.engine.ServiceState
import dev.mrbean.aibrowser.engine.TokenStore
import dev.mrbean.aibrowser.service.ServiceController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SettingsUiState(
    val tunnelToken: String = "",
    val showTunnelToken: Boolean = false,
    val tokens: List<ApiToken> = emptyList(),
    val addLabel: String = "",
    val mcpHost: String = "",
    val chromiumFlags: String = "",
    val extensions: List<String> = emptyList(),
    val rootfsVersion: String = "",
    val manifestUrl: String = "",
)

class SettingsViewModel(graph: AppGraph, application: Application) : AndroidViewModel(application) {

    private val paths = graph.paths
    private val config = graph.config
    private val installer = graph.installer
    private val supervisor = graph.supervisor
    private val tokenStore = TokenStore(paths.data)
    private val tunnelFile = SecretFile(File(paths.data, "tunnel.token"))
    private val mcpHostFile = SecretFile(File(paths.data, "mcp.host"))
    private val chromiumFlagsFile = File(paths.data, "chromium.flags")

    val appVersionName: String = versionName(application)

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    /** Incremented after every successful write; the screen shows a "Saved" snackbar. */
    private val _savedCount = MutableStateFlow(0)
    val savedCount: StateFlow<Int> = _savedCount.asStateFlow()

    /** The just-added API token, shown once in a dialog. */
    private val _pendingToken = MutableStateFlow<ApiToken?>(null)
    val pendingToken: StateFlow<ApiToken?> = _pendingToken.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val cfg = config.load()
                SettingsUiState(
                    tunnelToken = tunnelFile.readOrEmpty(),
                    tokens = tokenStore.list(),
                    mcpHost = cfg.mcpHost,
                    chromiumFlags = if (chromiumFlagsFile.isFile) chromiumFlagsFile.readText().trim() else "",
                    extensions = listExtensions(),
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

    fun toggleShowTunnelToken() {
        _state.update { it.copy(showTunnelToken = !it.showTunnelToken) }
    }

    fun saveTunnel() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { tunnelFile.write(_state.value.tunnelToken) }
            val tunnelState = supervisor.statuses.value["tunnel"]?.state
            when (tunnelState) {
                is ServiceState.Running ->
                    ServiceController.start(getApplication(), ServiceController.ACTION_RESTART, "tunnel")
                is ServiceState.Disabled ->
                    ServiceController.start(getApplication(), ServiceController.ACTION_START, "tunnel")
                else -> Unit
            }
            _savedCount.update { it + 1 }
        }
    }

    fun clearTunnel() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { tunnelFile.write("") }
            _state.update { it.copy(tunnelToken = "") }
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

    fun saveMcpHost() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                mcpHostFile.write(_state.value.mcpHost)
                config.save(config.load().copy(mcpHost = _state.value.mcpHost))
            }
            if (supervisor.statuses.value["mcp"]?.state is ServiceState.Running) {
                ServiceController.start(getApplication(), ServiceController.ACTION_RESTART, "mcp")
            }
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
            _savedCount.update { it + 1 }
        }
    }

    fun restartChromium() {
        ServiceController.start(getApplication(), ServiceController.ACTION_RESTART, "chromium")
    }

    // Rootfs

    fun uninstallRootfs() {
        viewModelScope.launch {
            ServiceController.start(getApplication(), ServiceController.ACTION_STOP_ALL)
            withContext(Dispatchers.IO) { installer.uninstall() }
        }
    }

    private fun listExtensions(): List<String> =
        File(paths.data, "extensions").listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?: emptyList()

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