package dev.mrbean.aibrowser.ui

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.mrbean.aibrowser.AiBrowserApp
import dev.mrbean.aibrowser.AppGraph
import dev.mrbean.aibrowser.engine.ApiToken
import dev.mrbean.aibrowser.engine.ConfigStore
import dev.mrbean.aibrowser.engine.InstallState
import dev.mrbean.aibrowser.engine.NativeBinaries
import dev.mrbean.aibrowser.engine.PrepareReport
import dev.mrbean.aibrowser.engine.SecretFile
import dev.mrbean.aibrowser.engine.SelfTest
import dev.mrbean.aibrowser.engine.SelfTestReport
import dev.mrbean.aibrowser.engine.TokenStore
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

const val ONBOARDING_STEP_COUNT = 6

/** A hostname is valid when it has no scheme, no slash and no spaces; empty is invalid. */
fun isValidHostname(host: String): Boolean {
    if (host.isEmpty()) return false
    if (host.contains("://")) return false
    if (host.contains('/')) return false
    if (host.any { it.isWhitespace() }) return false
    return true
}

private fun isOptionalHostnameValid(host: String): Boolean =
    host.isEmpty() || isValidHostname(host)

data class OnboardingUiState(
    val prepareBusy: Boolean = false,
    val prepareOk: Boolean = false,
    val prepareReport: PrepareReport? = null,
    val selfTestReport: SelfTestReport? = null,
    val manifestUrl: String = DEFAULT_MANIFEST_URL,
    val installState: InstallState = InstallState.Idle,
    val rootfsBusy: Boolean = false,
    val tunnelToken: String = "",
    val showTunnelToken: Boolean = false,
    val tunnelSaved: Boolean = false,
    val tunnelSkipped: Boolean = false,
    val tokenLabel: String = "agent",
    val tokens: List<ApiToken> = emptyList(),
    val pendingToken: ApiToken? = null,
    val mcpHost: String = "",
    val statusHost: String = "",
    val viewerHost: String = "",
)

/** The pure Next-enabling rule for every onboarding step, given the current state. */
fun nextEnabled(step: Int, state: OnboardingUiState): Boolean = when (step) {
    // Welcome: next needs the native-binaries self-test to have run proot cleanly.
    0 -> state.prepareOk
    // Rootfs: next needs the userland installed.
    1 -> state.installState is InstallState.Installed
    // Android checks: informational, always next.
    2 -> true
    // Tunnel: next needs Save or Skip, or a token that is already stored.
    3 -> state.tunnelSaved || state.tunnelSkipped || state.tunnelToken.isNotBlank()
    // API token: next needs at least one token in the store.
    4 -> state.tokens.isNotEmpty()
    // Hostnames: finish needs valid hostnames (MCP required, status and viewer optional).
    5 -> isValidHostname(state.mcpHost) &&
        isOptionalHostnameValid(state.statusHost) &&
        isOptionalHostnameValid(state.viewerHost)
    else -> false
}

class OnboardingViewModel(graph: AppGraph, application: Application) : AndroidViewModel(application) {

    private val paths = graph.paths
    private val selfTest = SelfTest(paths, graph.runner)
    private val installer = graph.installer
    private val config = graph.config
    private val supervisor = graph.supervisor
    private val tokenStore = TokenStore(paths.data)
    private val tunnelFile = SecretFile(File(paths.data, "tunnel.token"))
    private val mcpHostFile = SecretFile(File(paths.data, "mcp.host"))

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /** Becomes true once [finish] has completed; the screen then leaves the flow. */
    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    private var installJob: Job? = null

    init {
        val loaded = config.load()
        _state.value = OnboardingUiState(
            manifestUrl = loaded.mirrorUrl.ifBlank { DEFAULT_MANIFEST_URL },
            installState = installer.state.value,
            tunnelToken = tunnelFile.readOrEmpty(),
            tokens = tokenStore.list(),
            mcpHost = loaded.mcpHost,
            statusHost = loaded.statusHost,
            viewerHost = loaded.viewerHost,
        )
        viewModelScope.launch {
            installer.state.collect { installState ->
                _state.update { it.copy(installState = installState) }
            }
        }
        // The welcome step runs Prepare and the self-test automatically on entry.
        prepareAndSelfTest()
    }

    // Step 1: native binaries and self-test.

    fun prepareAndSelfTest() {
        if (_state.value.prepareBusy || _state.value.prepareOk) return
        recheck()
    }

    /** Re-runs Prepare and the self-test (Run again / Retry), always, even after a pass. */
    fun recheck() {
        if (_state.value.prepareBusy) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    prepareBusy = true,
                    prepareOk = false,
                    prepareReport = null,
                    selfTestReport = null,
                )
            }
            val prepare = withContext(Dispatchers.IO) { NativeBinaries.prepare(paths) }
            val report = withContext(Dispatchers.IO) { selfTest.run() }
            val passed = report.proot.exitCode == 0 && report.busybox.exitCode == 0
            _state.update {
                it.copy(
                    prepareBusy = false,
                    prepareOk = passed,
                    prepareReport = prepare,
                    selfTestReport = report,
                )
            }
        }
    }

    // Step 2: rootfs.

    fun onManifestUrlChange(value: String) {
        _state.update { it.copy(manifestUrl = value) }
    }

    fun resetManifestUrl() {
        _state.update { it.copy(manifestUrl = DEFAULT_MANIFEST_URL) }
        viewModelScope.launch(Dispatchers.IO) {
            val store = ConfigStore(paths.data)
            store.save(store.load().copy(mirrorUrl = ""))
        }
    }

    fun install() {
        if (installJob?.isActive == true) return
        _state.update { it.copy(rootfsBusy = true) }
        installJob = viewModelScope.launch {
            try {
                installer.install(_state.value.manifestUrl)
            } finally {
                installJob = null
                _state.update { it.copy(rootfsBusy = false) }
            }
        }
    }

    fun cancelInstall() {
        installJob?.cancel()
    }

    fun uninstall() {
        if (installJob?.isActive == true) return
        _state.update { it.copy(rootfsBusy = true) }
        installJob = viewModelScope.launch {
            try {
                installer.uninstall()
            } finally {
                installJob = null
                _state.update { it.copy(rootfsBusy = false) }
            }
        }
    }

    // Step 4: tunnel.

    fun onTunnelTokenChange(value: String) {
        _state.update { it.copy(tunnelToken = value) }
    }

    fun toggleShowTunnelToken() {
        _state.update { it.copy(showTunnelToken = !it.showTunnelToken) }
    }

    fun saveTunnel() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { tunnelFile.write(_state.value.tunnelToken) }
            _state.update { it.copy(tunnelSaved = true) }
        }
    }

    fun skipTunnel() {
        _state.update { it.copy(tunnelSkipped = true) }
    }

    // Step 5: API token.

    fun onTokenLabelChange(value: String) {
        _state.update { it.copy(tokenLabel = value) }
    }

    fun generateToken() {
        val label = _state.value.tokenLabel
        if (label.isBlank()) return
        viewModelScope.launch {
            val (added, tokens) = withContext(Dispatchers.IO) {
                val created = tokenStore.add(label)
                created to tokenStore.list()
            }
            _state.update { it.copy(tokens = tokens, pendingToken = added) }
        }
    }

    fun dismissPendingToken() {
        _state.update { it.copy(pendingToken = null) }
    }

    // Step 6: hostnames.

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
            persistHostnames()
            restartService(getApplication(), supervisor, "mcp")
            restartService(getApplication(), supervisor, "tunnel")
        }
    }

    /** Writes the hostnames (idempotent), marks setup complete, enables boot start and starts all. */
    fun finish() {
        viewModelScope.launch {
            persistHostnames()
            config.save(
                config.load().copy(
                    setupComplete = true,
                    startOnBoot = true,
                ),
            )
            ServiceController.start(getApplication(), ServiceController.ACTION_START_ALL)
            _finished.value = true
        }
    }

    private suspend fun persistHostnames() {
        withContext(Dispatchers.IO) {
            val current = _state.value
            mcpHostFile.write(current.mcpHost)
            config.save(
                config.load().copy(
                    mcpHost = current.mcpHost,
                    statusHost = current.statusHost,
                    viewerHost = current.viewerHost,
                ),
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AiBrowserApp
                OnboardingViewModel(app.graph, app)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    val finished by viewModel.finished.collectAsState()
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_STEP_COUNT })
    val copy = rememberClipboardCopy()
    val scope = rememberCoroutineScope()

    LaunchedEffect(finished) {
        if (finished) onFinished()
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "Step ${pagerState.currentPage + 1} of $ONBOARDING_STEP_COUNT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
            )
            LinearProgressIndicator(
                progress = { (pagerState.currentPage + 1) / ONBOARDING_STEP_COUNT.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> WelcomeStep(state, viewModel)
                    1 -> RootfsStep(state, viewModel)
                    2 -> AndroidChecksStep()
                    3 -> TunnelStep(state, viewModel)
                    4 -> ApiTokenStep(state, viewModel, copy)
                    5 -> HostnamesStep(state, viewModel, copy)
                }
            }
            val lastPage = pagerState.currentPage == ONBOARDING_STEP_COUNT - 1
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    },
                    enabled = pagerState.currentPage > 0,
                ) { Text("Back") }
                Spacer(Modifier.weight(1f))
                if (lastPage) {
                    Button(
                        onClick = viewModel::finish,
                        enabled = nextEnabled(pagerState.currentPage, state),
                    ) { Text("Finish") }
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        enabled = nextEnabled(pagerState.currentPage, state),
                    ) { Text("Next") }
                }
            }
        }
    }
}

@Composable
private fun StepScaffold(
    title: String,
    paragraph: String,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            paragraph,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun WelcomeStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepScaffold(
        title = "Checking the bundled tools",
        paragraph = "AiBrowser turns this phone into a headed Chromium that AI agents drive over " +
            "MCP from anywhere. A Debian userland runs inside the app under proot, and this step " +
            "checks that the bundled tools it needs actually run.",
    ) {
        ToolsCheckCard(
            report = state.prepareReport,
            selfTest = state.selfTestReport,
            onRunAgain = viewModel::recheck,
        )
    }
}

/** Step 1's result card: Running, Passed or Failed for the bundled-tools check. */
@Composable
private fun ToolsCheckCard(
    report: PrepareReport?,
    selfTest: SelfTestReport?,
    onRunAgain: () -> Unit,
) {
    val summary = summarize(report, selfTest)
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            when (summary.state) {
                SelfTestState.RUNNING -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Checking proot and busybox", style = MaterialTheme.typography.bodyMedium)
                }

                SelfTestState.PASSED -> {
                    var showDetails by remember { mutableStateOf(false) }
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Ready",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(56.dp),
                        )
                        Text(
                            "Ready",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            "${summary.links} symlinks prepared",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(
                            summary.prootVersion?.let { "proot $it runs" } ?: "proot runs",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text("busybox runs", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Nothing to do here. Tap Next.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Row(Modifier.padding(top = 4.dp)) {
                            TextButton(onClick = onRunAgain) { Text("Run again") }
                            TextButton(onClick = { showDetails = !showDetails }) { Text("Details") }
                        }
                        if (showDetails) {
                            Text(
                                summary.details,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                SelfTestState.FAILED -> {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Filled.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(56.dp),
                        )
                        Text(
                            "Something is wrong with the bundled tools",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            failureDetails(selfTest),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        )
                        Button(onClick = onRunAgain, modifier = Modifier.padding(top = 12.dp)) {
                            Text("Retry")
                        }
                        Text(
                            "If this keeps failing the APK is broken; reinstall it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** The failed card's monospace block: exit codes and stderr lines only. */
private fun failureDetails(selfTest: SelfTestReport?): String = buildString {
    selfTest?.let {
        appendLine("proot exit ${it.proot.exitCode}")
        if (it.proot.stderr.isNotBlank()) append(it.proot.stderr)
        appendLine("busybox exit ${it.busybox.exitCode}")
        if (it.busybox.stderr.isNotBlank()) append(it.busybox.stderr)
    }
}

@Composable
private fun RootfsStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepScaffold(
        title = "Debian userland",
        paragraph = "The services run inside a Debian userland that proot starts for the app. " +
            "It downloads once (250 to 350 MB compressed), is verified by sha256 and extracted.",
    ) {
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
        Text(
            "Extraction takes a few minutes on most phones; the screen can stay on or off.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun AndroidChecksStep() {
    StepScaffold(
        title = "Android checks",
        paragraph = "A few one-time Android settings keep the services alive in the background. " +
            "The rows show the current state; you can come back later. " +
            "Manufacturer ROMs add their own battery mode; the last row explains what to set.",
    ) {
        AndroidChecksCard()
    }
}

@Composable
private fun TunnelStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepScaffold(
        title = "Cloudflare tunnel",
        paragraph = "Optionally connects the app to your Cloudflare tunnel so agents can reach it " +
            "from anywhere. The phone itself works without it.",
    ) {
        TunnelTokenField(
            value = state.tunnelToken,
            show = state.showTunnelToken,
            onValueChange = viewModel::onTunnelTokenChange,
            onToggleShow = viewModel::toggleShowTunnelToken,
            onSave = viewModel::saveTunnel,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = viewModel::skipTunnel) {
            Text("You can add it later in Settings")
        }
    }
}

@Composable
private fun ApiTokenStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
    copy: (String) -> Unit,
) {
    StepScaffold(
        title = "API token",
        paragraph = "The gate accepts this token on the MCP and status URLs, so only your agents " +
            "can drive the browser. It is shown once; keep it secret.",
    ) {
        ApiTokenList(
            tokens = state.tokens,
            addLabel = state.tokenLabel,
            onAddLabelChange = viewModel::onTokenLabelChange,
            onAdd = viewModel::generateToken,
            addButtonLabel = "Generate",
        )
        state.pendingToken?.let { token ->
            Text(
                "Use it as ?token=<value> on the MCP and status URLs.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        token.token,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        copy(token.token)
                        viewModel.dismissPendingToken()
                    }) { Text("Copy") }
                }
            }
        }
    }
}

@Composable
private fun HostnamesStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
    copy: (String) -> Unit,
) {
    StepScaffold(
        title = "Hostnames",
        paragraph = "Three public hostnames you create in Cloudflare Zero Trust, one per service. " +
            "The status and viewer hostnames can stay empty for now.",
    ) {
        val mcpValid = isValidHostname(state.mcpHost)
        val statusValid = isOptionalHostnameValid(state.statusHost)
        val viewerValid = isOptionalHostnameValid(state.viewerHost)
        val valid = mcpValid && statusValid && viewerValid
        HostnameField(
            value = state.mcpHost,
            label = "MCP hostname",
            valid = mcpValid,
            onValueChange = viewModel::onMcpHostChange,
        )
        HostnameField(
            value = state.statusHost,
            label = "Status hostname",
            valid = statusValid,
            onValueChange = viewModel::onStatusHostChange,
            modifier = Modifier.padding(top = 8.dp),
        )
        HostnameField(
            value = state.viewerHost,
            label = "Viewer hostname",
            valid = viewerValid,
            onValueChange = viewModel::onViewerHostChange,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(Modifier.padding(top = 12.dp)) {
            Button(onClick = viewModel::saveHostnames, enabled = valid) { Text("Save") }
        }
        Text(
            "Create these three public hostnames in the Cloudflare dashboard:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            modifier = Modifier.padding(top = 12.dp),
        )
        GuideRow(state.mcpHost, "http://localhost:8931", copy)
        GuideRow(state.statusHost, "http://localhost:8932", copy)
        GuideRow(state.viewerHost, "http://localhost:6080", copy)
        Text(
            "The MCP and status hostnames need ?token=<token> on every request. " +
                "Put the viewer behind a Cloudflare Access login.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun HostnameField(
    value: String,
    label: String,
    valid: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = value.isNotEmpty() && !valid,
        supportingText = if (value.isNotEmpty() && !valid) {
            { Text("No scheme, slash or spaces.") }
        } else {
            null
        },
        modifier = modifier.fillMaxWidth(),
    )
}