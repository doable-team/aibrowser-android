package dev.mrbean.aibrowser.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    onGoToRepair: () -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
) {
    DashboardContent(
        onGoToRepair = onGoToRepair,
        viewModel = viewModel,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    )
}

/**
 * The service overview: Start all / Stop all / Restart all, then one card per
 * service. Non-scrolling; the caller supplies the scroll and padding. Reused by
 * [HomeScreen] below its preview strip.
 */
@Composable
fun DashboardContent(
    onGoToRepair: () -> Unit,
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier,
) {
    val statuses by viewModel.statuses.collectAsState()
    val rootfsInstalled by viewModel.rootfsInstalled.collectAsState()

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            nowMs = System.currentTimeMillis()
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
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
    // Refuses to start the services when the rootfs is missing.
    val startAll: () -> Unit = {
        if (!viewModel.rootfsReady()) {
            scope.launch { snackbarHostState.showSnackbar("Install the rootfs first") }
        } else {
            withPermission { viewModel.startAll() }
        }
    }
    val restartAll: () -> Unit = {
        if (!viewModel.rootfsReady()) {
            scope.launch { snackbarHostState.showSnackbar("Install the rootfs first") }
        } else {
            withPermission { viewModel.restartAll() }
        }
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val onCopyLog: (String) -> Unit = { text ->
        clipboard.setPrimaryClip(ClipData.newPlainText("service log", text))
    }
    val onShareLog: (String) -> Unit = { text ->
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share log"))
    }

    Box(modifier) {
        if (!rootfsInstalled) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Install the rootfs first", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "The services run inside the Debian userland. Set it up before starting them.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onGoToRepair) { Text("Go to Repair") }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) {
                    Button(onClick = startAll) { Text("Start all") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = viewModel::stopAll) { Text("Stop all") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = restartAll) { Text("Restart all") }
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
                        onRestart = { withPermission { viewModel.restart(def.name) } },
                        loadLogs = { viewModel.logLines(def.name) },
                        onCopyLog = onCopyLog,
                        onShareLog = onShareLog,
                        onClearLog = { viewModel.clearLog(def.name) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ServiceCard(
    def: ServiceDef,
    status: ServiceStatus?,
    nowMs: Long,
    onToggle: (Boolean) -> Unit,
    onRestart: () -> Unit,
    loadLogs: () -> List<String>,
    onCopyLog: (String) -> Unit,
    onShareLog: (String) -> Unit,
    onClearLog: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
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
                        color = statusChipColor(status?.state),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        "Restarts: ${status?.restarts ?: 0}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Row {
                    OutlinedButton(onClick = onRestart, enabled = isStarted(status?.state)) {
                        Text("Restart")
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = isStarted(status?.state),
                        onCheckedChange = onToggle,
                    )
                }
            }

            // A service stuck in a long backoff shows its last lines so the
            // reason for the failure is visible without opening the log viewer.
            val state = status?.state
            if (state is ServiceState.Backoff && state.attempt >= 6) {
                val tail = status.lastLines.takeLast(3)
                if (tail.isNotEmpty()) {
                    Text(
                        tail.joinToString("\n"),
                        color = MaterialTheme.colorScheme.error,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            var showLogs by remember { mutableStateOf(false) }
            TextButton(onClick = { showLogs = !showLogs }) {
                Text(if (showLogs) "Hide logs" else "Show logs")
            }
            if (showLogs) {
                HorizontalDivider()
                val lines = loadLogs()
                val shown = lines.takeLast(40)
                Text(
                    if (shown.isEmpty()) "no output yet"
                    else shown.joinToString("\n"),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = { onCopyLog(shown.joinToString("\n")) }) { Text("Copy") }
                    TextButton(onClick = { onShareLog(lines.joinToString("\n")) }) { Text("Share") }
                    TextButton(onClick = onClearLog) { Text("Clear") }
                }
            }
        }
    }
}

private fun stateChip(state: ServiceState?, nowMs: Long): String = when (state) {
    is ServiceState.Disabled -> "Disabled — ${state.reason}"
    is ServiceState.Failed -> "Failed — ${state.reason}"
    is ServiceState.Starting -> "Starting"
    is ServiceState.Running -> "Running · ${formatDuration(nowMs - state.sinceMs)}"
    is ServiceState.Backoff -> {
        val secs = maxOf(0L, (state.retryAtMs - nowMs + 999) / 1000)
        "Retry in $secs s, attempt ${state.attempt}"
    }
    is ServiceState.Stopped -> "Stopped"
    null -> "Stopped"
}

@Composable
private fun statusChipColor(state: ServiceState?): Color = when (state) {
    is ServiceState.Running -> MaterialTheme.colorScheme.secondary
    is ServiceState.Starting, is ServiceState.Backoff -> MaterialTheme.colorScheme.tertiary
    is ServiceState.Failed -> MaterialTheme.colorScheme.error
    is ServiceState.Stopped, is ServiceState.Disabled, null -> MaterialTheme.colorScheme.outline
}

private fun isStarted(state: ServiceState?): Boolean = when (state) {
    is ServiceState.Running, is ServiceState.Starting, is ServiceState.Backoff -> true
    else -> false
}

internal fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    if (totalSec <= 0) return "0 s"
    val days = totalSec / 86_400
    val hours = (totalSec % 86_400) / 3_600
    val minutes = (totalSec % 3_600) / 60
    val seconds = totalSec % 60
    return when {
        days > 0 -> "$days d ${two(hours)} h ${two(minutes)} m"
        hours > 0 -> "$hours h ${two(minutes)} m ${two(seconds)} s"
        minutes > 0 -> "$minutes m ${two(seconds)} s"
        else -> "$seconds s"
    }
}

private fun two(n: Long): String = n.toString().padStart(2, '0')