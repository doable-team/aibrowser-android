package dev.mrbean.aibrowser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * The Repair page: the setup tools kept after onboarding, reached from Settings.
 * It holds the native binaries card, the rootfs card (Install / Reinstall /
 * Uninstall) and the Android checks card, plus a way to run the onboarding
 * flow again from the start.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepairScreen(
    onBack: () -> Unit,
    onRunOnboardingAgain: () -> Unit,
    viewModel: SetupViewModel = viewModel(factory = SetupViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Repair and reinstall") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                NativeBinariesCard(
                    busy = state.busy,
                    report = state.report,
                    onPrepare = viewModel::prepare,
                    onRunSelfTest = viewModel::runSelfTest,
                )
                Spacer(Modifier.height(12.dp))
                RootfsCard(
                    manifestUrl = state.manifestUrl,
                    installState = state.installState,
                    rootfsBusy = state.rootfsBusy,
                    onManifestUrlChange = viewModel::onManifestUrlChange,
                    onResetManifestUrl = viewModel::resetManifestUrl,
                    onInstall = viewModel::install,
                    onCancelInstall = viewModel::cancelInstall,
                    onUninstall = viewModel::uninstall,
                )
                Spacer(Modifier.height(12.dp))
                AndroidChecksCard()
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onRunOnboardingAgain,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Run onboarding again")
                }
                Text(
                    "This sets setup to incomplete and returns to the step-by-step flow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}