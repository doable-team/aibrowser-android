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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.mrbean.aibrowser.engine.ConfigStore
import dev.mrbean.aibrowser.engine.InstallState
import dev.mrbean.aibrowser.engine.Paths
import dev.mrbean.aibrowser.engine.PhantomProcessGuard
import dev.mrbean.aibrowser.engine.UpdateStatus
import dev.mrbean.aibrowser.engine.oemGuidance
import java.util.Locale

/** The app's card: surface fill, 16 dp corners and a 1 dp outline, no shadow. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        content = content,
    )
}

/**
 * The native binaries / self-test card: creates the bin/ and lib/ symlinks and
 * runs `proot --version` and `busybox echo ok` as a self-test. Shared by the
 * onboarding, the Repair page and the old Setup screen.
 */
@Composable
fun NativeBinariesCard(
    busy: Boolean,
    report: String,
    onPrepare: () -> Unit,
    onRunSelfTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text("Native binaries", style = MaterialTheme.typography.titleMedium)
            Text(
                "Creates the bin/ and lib/ symlinks for the bundled native binaries " +
                    "and checks that proot and busybox run.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(Modifier.padding(top = 12.dp)) {
                Button(onClick = onPrepare, enabled = !busy) { Text("Prepare") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onRunSelfTest, enabled = !busy) { Text("Run self-test") }
            }
            if (report.isNotEmpty()) {
                Text(
                    report,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

/**
 * The rootfs card: manifest URL (with its Default button), Install / Reinstall
 * (the label flips once installed), Cancel, Uninstall and the install progress.
 * Shared by the onboarding, the Repair page and the old Setup screen.
 */
@Composable
fun RootfsCard(
    manifestUrl: String,
    installState: InstallState,
    rootfsBusy: Boolean,
    onManifestUrlChange: (String) -> Unit,
    onResetManifestUrl: () -> Unit,
    onInstall: () -> Unit,
    onCancelInstall: () -> Unit,
    onUninstall: () -> Unit,
    updateStatus: UpdateStatus? = null,
    onCheckUpdates: (() -> Unit)? = null,
    onRunUpdate: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text("Rootfs", style = MaterialTheme.typography.titleMedium)
            Text(
                "Downloads, verifies and extracts the Debian userland tarball into the app's storage.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedTextField(
                value = manifestUrl,
                onValueChange = onManifestUrlChange,
                label = { Text("Manifest URL") },
                enabled = !rootfsBusy,
                singleLine = true,
                trailingIcon = {
                    if (manifestUrl != DEFAULT_MANIFEST_URL) {
                        TextButton(onClick = onResetManifestUrl, enabled = !rootfsBusy) { Text("Default") }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
            Row(Modifier.padding(top = 12.dp)) {
                val installLabel = if (installState is InstallState.Installed) "Reinstall" else "Install"
                Button(onClick = onInstall, enabled = !rootfsBusy) { Text(installLabel) }
                if (rootfsBusy) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onCancelInstall) { Text("Cancel") }
                }
                if (installState is InstallState.Installed) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onUninstall, enabled = !rootfsBusy) { Text("Uninstall") }
                }
            }
            if (updateStatus != null) {
                RootfsUpdateSection(
                    updateStatus = updateStatus,
                    busy = rootfsBusy,
                    onCheck = onCheckUpdates ?: {},
                    onUpdate = onRunUpdate ?: {},
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            RootfsProgress(installState)
            if (installState is InstallState.Failed) {
                Row(Modifier.padding(top = 8.dp)) {
                    Button(onClick = onInstall, enabled = !rootfsBusy) { Text("Retry") }
                }
            }
        }
    }
}

/** The install progress card: the linear bar plus the status text (download with
 *  megabytes and speed, verifying, extracting with the percentage, file count
 *  and timing). Shared by the install/reinstall card and the update flow. */
@Composable
internal fun RootfsProgress(installState: InstallState) {
    if (isRunning(installState)) {
        Spacer(Modifier.height(16.dp))
        val progress = progressOf(installState)
        if (progress != null) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
    val status = installStatusText(installState)
    if (status.isNotEmpty()) {
        Text(
            status,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** The Android checks: notifications, battery, the OEM battery mode and child process limit. */
@Composable
fun AndroidChecksCard() {
    val context = LocalContext.current
    var notificationGranted by remember { mutableStateOf(isNotificationGranted(context)) }
    var batteryIgnored by remember { mutableStateOf(isBatteryIgnored(context)) }
    val oemStore = remember { ConfigStore(Paths.from(context).data) }
    val oem = remember { oemGuidance(Build.MANUFACTURER, Build.BRAND) }
    var oemBatteryDone by remember { mutableStateOf(oemStore.load().oemBatteryDone) }

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

    AppCard(modifier = Modifier.fillMaxWidth()) {
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
                    "Unrestricted; Android will not stop the services."
                } else {
                    "Set Battery use to Unrestricted, or Android stops the services."
                },
                buttonText = if (batteryIgnored) "App details" else "Request",
                onButton = if (batteryIgnored) openAppDetails else requestBattery,
            )
            if (oem != null) {
                OemBatteryRow(
                    name = oem.name,
                    detail = buildString {
                        append(oem.steps)
                        if (oem.alsoAutostart) {
                            append(" Without auto launch the services will not start after a reboot.")
                        }
                    },
                    done = oemBatteryDone,
                    onOpenAppInfo = openAppDetails,
                    onToggle = { checked ->
                        oemBatteryDone = checked
                        runCatching { oemStore.save(oemStore.load().copy(oemBatteryDone = checked)) }
                    },
                )
            }
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
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusIcon(ok)
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        }
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onButton) { Text(buttonText) }
        }
    }
}

/**
 * The OEM-specific battery mode row: only shown when the manufacturer ROM adds
 * its own battery setting on top of the AOSP exemption, which no app can read,
 * so the operator sets it by hand and confirms with the checkbox.
 */
@Composable
private fun OemBatteryRow(
    name: String,
    detail: String,
    done: Boolean,
    onOpenAppInfo: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusIcon(done)
            Spacer(Modifier.width(12.dp))
            Text("Background activity ($name)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        }
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = done, onCheckedChange = onToggle)
            Text("I have set this", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onOpenAppInfo) { Text("Open app info") }
        }
    }
}

@Composable
private fun ChildProcessLimitRow(onCheckAgain: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(PhantomProcessGuard.ensureDisabled(context)) }
    var canWrite by remember { mutableStateOf(PhantomProcessGuard.canWrite(context)) }
    val disabled = state == PhantomProcessGuard.State.DISABLED
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusIcon(disabled)
            Spacer(Modifier.width(12.dp))
            Text("Child process limit", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        }
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
        if (!canWrite) {
            Text(
                PhantomProcessGuard.GRANT_COMMAND,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
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

@Composable
fun StatusIcon(ok: Boolean?) {
    when (ok) {
        true -> Icon(
            Icons.Filled.CheckCircle,
            contentDescription = "OK",
            tint = MaterialTheme.colorScheme.secondary,
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

internal fun isRunning(state: InstallState): Boolean = when (state) {
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

internal fun progressOf(state: InstallState): Float? = when (state) {
    is InstallState.Downloading -> if (state.total > 0) (state.done.toFloat() / state.total).coerceIn(0f, 1f) else null
    is InstallState.Verifying -> if (state.total > 0) (state.done.toFloat() / state.total).coerceIn(0f, 1f) else null
    is InstallState.Extracting -> if (state.total > 0) (state.done.toFloat() / state.total).coerceIn(0f, 0.99f) else null
    else -> null
}

private const val FINAL_TOUCHES = "Doing final touches..."

internal fun installStatusText(state: InstallState): String = when (state) {
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
        if (state.total > 0) {
            // tar keeps working after the last entry (delayed directory
            // restore), so the bar never claims 100% before Installed.
            val finishing = state.done >= state.total
            val percent = if (finishing) 99 else state.done * 100 / state.total
            val timing = buildString {
                append("${formatElapsed(state.elapsedSec.toLong())} elapsed")
                if (!finishing && state.elapsedSec >= 10 && state.done > 0) {
                    val estimate = state.elapsedSec * (state.total - state.done) / state.done
                    append(", about ${formatRemaining(estimate)} left")
                }
            }
            listOf(
                if (finishing) FINAL_TOUCHES else "Extracting $percent%",
                "${formatCount(state.done)} of ${formatCount(state.total)} files",
                timing,
            ).joinToString("\n")
        } else {
            listOf(
                "Extracting",
                "${formatElapsed(state.elapsedSec.toLong())} elapsed",
                state.lastPath,
            ).filter { it.isNotEmpty() }.joinToString("\n")
        }
    is InstallState.WritingFiles -> FINAL_TOUCHES
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

private fun formatElapsed(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return if (minutes == 0L) "$secs s" else "$minutes m ${"%02d".format(secs)} s"
}

private fun formatRemaining(seconds: Long): String = when {
    seconds < 10 -> "a few seconds"
    seconds < 60 -> "$seconds s"
    else -> "${(seconds + 30) / 60} min"
}

private fun formatCount(value: Long): String {
    val digits = value.toString()
    val out = StringBuilder(digits.length + digits.length / 3)
    var count = 0
    for (i in digits.indices) {
        if (count > 0 && count % 3 == 0) out.append('\u202F')
        out.append(digits[digits.length - 1 - i])
        count++
    }
    return out.reverse().toString()
}