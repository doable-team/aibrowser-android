package dev.mrbean.aibrowser.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mrbean.aibrowser.engine.ServiceDef
import dev.mrbean.aibrowser.engine.ServiceState
import dev.mrbean.aibrowser.engine.ServiceStatus
import dev.mrbean.aibrowser.engine.Services
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    onGoToSetup: () -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
) {
    val statuses by viewModel.statuses.collectAsState()
    val rootfsInstalled by viewModel.rootfsInstalled.collectAsState()

    if (!rootfsInstalled) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Install the rootfs first", style = MaterialTheme.typography.titleMedium)
                Text(
                    "The services run inside the Debian userland. Set it up before starting them.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onGoToSetup) { Text("Go to Setup") }
            }
        }
        return
    }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            nowMs = System.currentTimeMillis()
        }
    }

    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        pendingAction?.invoke()
        pendingAction = null
    }
    val withPermission: (action: () -> Unit) -> Unit = { action ->
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingAction = action
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val onCopyLog: (String) -> Unit = { text ->
        clipboard.setPrimaryClip(ClipData.newPlainText("service log", text))
    }

    val running = statuses.values.count { it.state is ServiceState.Running }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            "$running of ${Services.all.size} running",
            style = MaterialTheme.typography.headlineSmall,
        )
        Row(Modifier.padding(top = 12.dp)) {
            Button(onClick = { withPermission { viewModel.startAll() } }) { Text("Start all") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = viewModel::stopAll) { Text("Stop all") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { withPermission { viewModel.restartAll() } }) { Text("Restart all") }
        }
        Spacer(Modifier.height(16.dp))
        Services.all.forEach { def ->
            ServiceCard(
                def = def,
                status = statuses[def.name],
                nowMs = nowMs,
                onToggle = { on ->
                    if (on) withPermission { viewModel.start(def.name) }
                    else viewModel.stop(def.name)
                },
                loadLogs = { viewModel.logLines(def.name) },
                onCopyLog = onCopyLog,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ServiceCard(
    def: ServiceDef,
    status: ServiceStatus?,
    nowMs: Long,
    onToggle: (Boolean) -> Unit,
    loadLogs: () -> List<String>,
    onCopyLog: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(def.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        def.description,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        stateChip(status?.state, nowMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        "Restarts: ${status?.restarts ?: 0}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Switch(
                    checked = isStarted(status?.state),
                    onCheckedChange = onToggle,
                )
            }

            var showLogs by remember { mutableStateOf(false) }
            TextButton(onClick = { showLogs = !showLogs }) {
                Text(if (showLogs) "Hide logs" else "Show logs")
            }
            if (showLogs) {
                HorizontalDivider()
                val lines = loadLogs().takeLast(40)
                Text(
                    if (lines.isEmpty()) "no output yet"
                    else lines.joinToString("\n"),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = { onCopyLog(lines.joinToString("\n")) }) { Text("Copy") }
                }
            }
        }
    }
}

private fun stateChip(state: ServiceState?, nowMs: Long): String = when (state) {
    is ServiceState.Disabled -> "Disabled — ${state.reason}"
    is ServiceState.Starting -> "Starting"
    is ServiceState.Running -> "Running · ${formatDuration(nowMs - state.sinceMs)}"
    is ServiceState.Backoff -> {
        val secs = maxOf(0L, (state.retryAtMs - nowMs + 999) / 1000)
        "Retry in $secs s, attempt ${state.attempt}"
    }
    is ServiceState.Stopped -> "Stopped"
    null -> "Stopped"
}

private fun isStarted(state: ServiceState?): Boolean = when (state) {
    is ServiceState.Running, is ServiceState.Starting, is ServiceState.Backoff -> true
    else -> false
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return if (minutes > 0) "$minutes m $seconds s" else "$seconds s"
}