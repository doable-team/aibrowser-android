package dev.mrbean.aibrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mrbean.aibrowser.engine.ApiToken

@Composable
fun SettingsScreen(
    onGoToRepair: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    val savedCount by viewModel.savedCount.collectAsState()
    val pendingToken by viewModel.pendingToken.collectAsState()
    val copy = rememberClipboardCopy()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(savedCount) {
        if (savedCount > 0) snackbarHostState.showSnackbar("Saved")
    }

    var tokenToRemove by remember { mutableStateOf<ApiToken?>(null) }
    var confirmUninstall by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            RepairCard(onGoToRepair)
            Spacer(Modifier.height(12.dp))
            TunnelCard(state, viewModel)
            Spacer(Modifier.height(12.dp))
            StartupCard(state, viewModel)
            Spacer(Modifier.height(12.dp))
            ApiTokensCard(state, viewModel, onRemove = { tokenToRemove = it })
            Spacer(Modifier.height(12.dp))
            HostnamesCard(state, viewModel, copy)
            Spacer(Modifier.height(12.dp))
            ChromiumCard(state, viewModel)
            Spacer(Modifier.height(12.dp))
            SettingsRootfsCard(state, onGoToRepair, onUninstall = { confirmUninstall = true })
            Spacer(Modifier.height(12.dp))
            AboutCard(state, viewModel)
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }

    tokenToRemove?.let { token ->
        AlertDialog(
            onDismissRequest = { tokenToRemove = null },
            title = { Text("Remove token?") },
            text = {
                Text(
                    "Remove \"${token.label}\"? Requests using this token stop working at once.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeToken(token.token)
                    tokenToRemove = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { tokenToRemove = null }) { Text("Cancel") }
            },
        )
    }

    pendingToken?.let { token ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPendingToken,
            title = { Text("Token added") },
            text = {
                Column {
                    Text("Use it as ?token=<value> on the MCP and status URLs.")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        token.token,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    copy(token.token)
                    viewModel.dismissPendingToken()
                }) { Text("Copy") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPendingToken) { Text("Done") }
            },
        )
    }

    if (confirmUninstall) {
        AlertDialog(
            onDismissRequest = { confirmUninstall = false },
            title = { Text("Uninstall rootfs?") },
            text = {
                Text(
                    "Stops all services and deletes the Debian userland. " +
                        "It will need to be downloaded again.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUninstall = false
                    viewModel.uninstallRootfs()
                }) { Text("Uninstall") }
            },
            dismissButton = {
                TextButton(onClick = { confirmUninstall = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun RepairCard(onGoToRepair: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Repair and reinstall", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Rerun the setup tools: native binaries, rootfs and Android checks.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onGoToRepair) { Text("Open") }
        }
    }
}

@Composable
private fun TunnelCard(state: SettingsUiState, viewModel: SettingsViewModel) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Tunnel", style = MaterialTheme.typography.titleMedium)
            TunnelTokenField(
                value = state.tunnelToken,
                show = state.showTunnelToken,
                onValueChange = viewModel::onTunnelTokenChange,
                onToggleShow = viewModel::toggleShowTunnelToken,
                onSave = viewModel::saveTunnel,
                onClear = viewModel::clearTunnel,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                "From Zero Trust, Networks, Tunnels: the token of your tunnel.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun StartupCard(state: SettingsUiState, viewModel: SettingsViewModel) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Startup", style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Start on boot", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Restarts all services after a reboot. " +
                            "On by default once the rootfs is installed.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = state.startOnBoot,
                    onCheckedChange = viewModel::setStartOnBoot,
                )
            }
        }
    }
}

@Composable
private fun ApiTokensCard(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onRemove: (ApiToken) -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("API tokens", style = MaterialTheme.typography.titleMedium)
            Text(
                "The gate accepts these on the status and MCP URLs.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            ApiTokenList(
                tokens = state.tokens,
                addLabel = state.addLabel,
                onAddLabelChange = viewModel::onAddLabelChange,
                onAdd = viewModel::addToken,
                onRemove = onRemove,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun HostnamesCard(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    copy: (String) -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Hostnames", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.mcpHost,
                onValueChange = viewModel::onMcpHostChange,
                label = { Text("MCP hostname") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
            OutlinedTextField(
                value = state.statusHost,
                onValueChange = viewModel::onStatusHostChange,
                label = { Text("Status hostname") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            OutlinedTextField(
                value = state.viewerHost,
                onValueChange = viewModel::onViewerHostChange,
                label = { Text("Viewer hostname") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            Row(Modifier.padding(top = 12.dp)) {
                Button(onClick = viewModel::saveHostnames) { Text("Save") }
            }
            Text(
                "Create these three public hostnames in the Cloudflare dashboard:",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            GuideRow(state.mcpHost, "http://localhost:8931", copy)
            GuideRow(state.statusHost, "http://localhost:8932", copy)
            GuideRow(state.viewerHost, "http://localhost:6080", copy)
            Text(
                "The MCP and status hostnames need ?token=<token> on every request. " +
                    "Put the viewer behind a Cloudflare Access login.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ChromiumCard(state: SettingsUiState, viewModel: SettingsViewModel) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Chromium", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.chromiumFlags,
                onValueChange = viewModel::onChromiumFlagsChange,
                label = { Text("Extra flags (one per line)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
            Row(Modifier.padding(top = 12.dp)) {
                Button(onClick = viewModel::saveChromiumFlags) { Text("Save") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = viewModel::restartChromium) { Text("Restart chromium") }
            }
            Text(
                if (state.extensions.isEmpty()) {
                    "No extensions installed."
                } else {
                    "Extensions: ${state.extensions.joinToString(", ")}"
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "To add one, copy an unpacked extension folder into " +
                    "data/extensions with a file manager or adb, then restart chromium.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SettingsRootfsCard(
    state: SettingsUiState,
    onGoToRepair: () -> Unit,
    onUninstall: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Rootfs", style = MaterialTheme.typography.titleMedium)
            Text(
                "Installed version: ${state.rootfsVersion.ifEmpty { "not installed" }}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "Manifest: ${state.manifestUrl}",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(Modifier.padding(top = 12.dp)) {
                Button(onClick = onGoToRepair) { Text("Reinstall") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onUninstall) { Text("Uninstall") }
            }
        }
    }
}

@Composable
private fun AboutCard(state: SettingsUiState, viewModel: SettingsViewModel) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("About", style = MaterialTheme.typography.titleMedium)
            Text(
                "App: ${viewModel.appVersionName}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "Rootfs: ${state.rootfsVersion.ifEmpty { "not installed" }}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Bundled proot, busybox and tar from termux-packages (GPL); " +
                    "Debian rootfs; MIT app.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}