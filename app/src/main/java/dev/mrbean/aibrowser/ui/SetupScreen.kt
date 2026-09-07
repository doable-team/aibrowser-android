package dev.mrbean.aibrowser.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import dev.mrbean.aibrowser.engine.PhantomProcessGuard
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mrbean.aibrowser.engine.InstallState
import java.util.Locale

@Composable
fun SetupScreen(viewModel: SetupViewModel = viewModel(factory = SetupViewModel.Factory)) {
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
        Spacer(Modifier.height(12.dp))
        AndroidChecksCard()
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

@Composable
private fun AndroidChecksCard() {
    val context = LocalContext.current
    var notificationGranted by remember { mutableStateOf(isNotificationGranted(context)) }
    var batteryIgnored by remember { mutableStateOf(isBatteryIgnored(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { notificationGranted = isNotificationGranted(context) }

    val checkAgain: () -> Unit = {
        notificationGranted = isNotificationGranted(context)
        batteryIgnored = isBatteryIgnored(context)
    }

    val requestBattery: () -> Unit = {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }.onFailure {
            // Some OEMs do not provide the request activity; the settings list
            // still lets the operator reach the exemption dialog.
            runCatching {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        batteryIgnored = isBatteryIgnored(context)
    }

    val openAppDetails: () -> Unit = {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Android checks", style = MaterialTheme.typography.titleMedium)
            Text(
                "A few one-time Android settings keep the services alive in the background.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            CheckRow(
                label = "Notifications",
                ok = notificationGranted,
                detail = if (notificationGranted) "Granted" else "Notifications are off on Android 13+",
                buttonText = if (notificationGranted) "Granted" else "Request",
                onButton = {
                    if (Build.VERSION.SDK_INT >= 33 && !notificationGranted) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        checkAgain()
                    }
                },
            )
            // One setting under two names: the exemption request sets what the
            // app's Battery page calls "Unrestricted" (Android 12+) or "Not
            // optimised" (older versions).
            CheckRow(
                label = "Battery: unrestricted",
                ok = batteryIgnored,
                detail = if (batteryIgnored) {
                    "Battery use is unrestricted; Android will not stop the services in the background."
                } else {
                    "Set the app's Battery use to Unrestricted, or Android stops the services in the background."
                },
                buttonText = if (batteryIgnored) "App details" else "Request",
                onButton = if (batteryIgnored) openAppDetails else requestBattery,
            )
            ChildProcessLimitRow(onCheckAgain = checkAgain)
        }
    }
}

@Composable
private fun CheckRow(
    label: String,
    ok: Boolean?,
    detail: String,
    buttonText: String,
    onButton: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusIcon(ok)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onButton) { Text(buttonText) }
    }
}

@Composable
private fun ChildProcessLimitRow(onCheckAgain: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(PhantomProcessGuard.ensureDisabled(context)) }
    var canWrite by remember { mutableStateOf(PhantomProcessGuard.canWrite(context)) }
    val disabled = state == PhantomProcessGuard.State.DISABLED
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusIcon(disabled)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Child process limit", style = MaterialTheme.typography.titleSmall)
            Text(
                if (disabled) {
                    if (canWrite) "Restriction disabled; the app keeps it off across reboots."
                    else "Restriction disabled by the developer option. It can reset on reboot: " +
                        "grant the app the secure-settings permission once to make it permanent."
                } else {
                    "Android kills an app's child processes beyond 32; the browser services count. " +
                        "Turn on \"Disable child process restrictions\" in Developer options, or grant " +
                        "the app permission to do it itself."
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (!canWrite) {
                Text(
                    PhantomProcessGuard.GRANT_COMMAND,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(Modifier.padding(top = 4.dp)) {
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                    }
                }) { Text("Developer options") }
                TextButton(onClick = {
                    state = PhantomProcessGuard.ensureDisabled(context)
                    canWrite = PhantomProcessGuard.canWrite(context)
                    onCheckAgain()
                }) { Text("Check again") }
            }
        }
    }
}

@Composable
private fun StatusIcon(ok: Boolean?) {
    when (ok) {
        true -> Icon(
            Icons.Filled.CheckCircle,
            contentDescription = "OK",
            tint = Color(0xFF4CAF50),
        )
        false -> Icon(
            Icons.Filled.Close,
            contentDescription = "Not OK",
            tint = MaterialTheme.colorScheme.error,
        )
        null -> Icon(
            Icons.Filled.Info,
            contentDescription = "Info",
            tint = MaterialTheme.colorScheme.tertiary,
        )
    }
}

private fun isNotificationGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun isBatteryIgnored(context: Context): Boolean {
    val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return power.isIgnoringBatteryOptimizations(context.packageName)
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