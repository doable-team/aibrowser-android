package dev.mrbean.aibrowser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mrbean.aibrowser.engine.InstallState
import java.util.Locale

@Composable
fun SetupScreen(viewModel: SetupViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        NativeBinariesCard(state, viewModel)
        Spacer(Modifier.height(12.dp))
        RootfsCard(state, viewModel)
    }
}

@Composable
private fun NativeBinariesCard(state: SetupUiState, viewModel: SetupViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Native binaries", style = MaterialTheme.typography.titleMedium)
            Text(
                "Creates the bin/ and lib/ symlinks for the bundled native binaries " +
                    "and checks that proot and busybox run.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(Modifier.padding(top = 12.dp)) {
                Button(onClick = viewModel::prepare, enabled = !state.busy) { Text("Prepare") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = viewModel::runSelfTest, enabled = !state.busy) { Text("Run self-test") }
            }
            if (state.report.isNotEmpty()) {
                Text(
                    state.report,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun RootfsCard(state: SetupUiState, viewModel: SetupViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Rootfs", style = MaterialTheme.typography.titleMedium)
            Text(
                "Downloads, verifies and extracts the Debian userland tarball into the app's storage.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedTextField(
                value = state.manifestUrl,
                onValueChange = viewModel::onManifestUrlChange,
                label = { Text("Manifest URL") },
                enabled = !state.rootfsBusy,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
            Row(Modifier.padding(top = 12.dp)) {
                Button(onClick = viewModel::install, enabled = !state.rootfsBusy) { Text("Install") }
                if (state.rootfsBusy) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = viewModel::cancelInstall) { Text("Cancel") }
                }
                if (state.installState is InstallState.Installed) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = viewModel::uninstall, enabled = !state.rootfsBusy) { Text("Uninstall") }
                }
            }
            if (isRunning(state.installState)) {
                Spacer(Modifier.height(16.dp))
                val progress = progressOf(state.installState)
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            val status = installStatusText(state.installState)
            if (status.isNotEmpty()) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (state.installState is InstallState.Failed) {
                Row(Modifier.padding(top = 8.dp)) {
                    Button(onClick = viewModel::install, enabled = !state.rootfsBusy) { Text("Retry") }
                }
            }
        }
    }
}

private fun isRunning(state: InstallState): Boolean = when (state) {
    is InstallState.FetchingManifest,
    is InstallState.Downloading,
    is InstallState.Verifying,
    is InstallState.Extracting,
    is InstallState.WritingFiles,
    -> true

    is InstallState.Idle,
    is InstallState.Installed,
    is InstallState.Failed,
    -> false
}

private fun progressOf(state: InstallState): Float? = when (state) {
    is InstallState.Downloading -> if (state.total > 0) (state.done.toFloat() / state.total).coerceIn(0f, 1f) else null
    is InstallState.Verifying -> if (state.total > 0) (state.done.toFloat() / state.total).coerceIn(0f, 1f) else null
    else -> null
}

private fun installStatusText(state: InstallState): String = when (state) {
    is InstallState.Idle -> "Not installed"
    is InstallState.FetchingManifest -> "Fetching manifest"
    is InstallState.Downloading -> buildString {
        append("Downloading ")
        append(formatBytes(state.done))
        if (state.total >= 0) append(" of ${formatBytes(state.total)}")
        append(", ${formatSpeed(state.bytesPerSecond)}")
    }
    is InstallState.Verifying -> {
        val percent = if (state.total > 0) state.done * 100 / state.total else 0
        "Verifying $percent%"
    }
    is InstallState.Extracting ->
        if (state.lastPath.isEmpty()) "Extracting, ${state.elapsedSec} s"
        else "Extracting, ${state.elapsedSec} s, ${state.lastPath}"
    is InstallState.WritingFiles -> "Writing files"
    is InstallState.Installed -> "Installed ${state.version}"
    is InstallState.Failed -> state.message
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    if (mb >= 1000) return String.format(Locale.US, "%.1f GB", mb / 1000)
    if (mb >= 1) return String.format(Locale.US, "%.1f MB", mb)
    val kb = bytes / 1_000.0
    if (kb >= 1) return String.format(Locale.US, "%.1f KB", kb)
    return "$bytes B"
}

private fun formatSpeed(bytesPerSecond: Long): String = formatBytes(bytesPerSecond) + "/s"