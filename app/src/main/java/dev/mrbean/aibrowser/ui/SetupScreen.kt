package dev.mrbean.aibrowser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SetupScreen(viewModel: SetupViewModel = viewModel(factory = SetupViewModel.Factory)) {
    val state by viewModel.state.collectAsState()
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
    }
}