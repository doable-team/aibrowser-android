package dev.mrbean.aibrowser.ui

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mrbean.aibrowser.AiBrowserApp
import dev.mrbean.aibrowser.engine.Services
import dev.mrbean.aibrowser.engine.ServiceState
import dev.mrbean.aibrowser.service.ServiceController

/**
 * The Home tab: a live 16:9 preview strip of the desktop on top (the whole
 * 1280x720 desktop scaled down, with a health chip and a Fullscreen button),
 * the viewer controls (View only, Reload, Fullscreen) directly under it, then
 * the service overview below. Fullscreen shows the viewer alone filling the
 * screen with an Exit button; the Back gesture also exits it.
 */
@Composable
fun HomeScreen(
    onGoToRepair: () -> Unit,
    fullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
) {
    val statuses by viewModel.statuses.collectAsState()
    val running = statuses.values.count { it.state is ServiceState.Running }
    val novncRunning = statuses["novnc"]?.state is ServiceState.Running
    val context = LocalContext.current
    val graph = remember { AiBrowserApp.graphOf(context) }
    var viewOnly by remember { mutableStateOf(graph.config.load().viewOnly) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    val onStartNovnc = {
        ServiceController.start(context, ServiceController.ACTION_START, "novnc")
    }
    val onViewOnlyChange: (Boolean) -> Unit = { on ->
        viewOnly = on
        graph.config.save(graph.config.load().copy(viewOnly = on))
    }

    // The system Back gesture also exits the fullscreen viewer.
    BackHandler(enabled = fullscreen) { onFullscreenChange(false) }

    if (fullscreen) {
        FullscreenViewer(
            novncRunning = novncRunning,
            onStartNovnc = onStartNovnc,
            viewOnly = viewOnly,
            onViewOnlyChange = onViewOnlyChange,
            onExit = { onFullscreenChange(false) },
            onReady = { webView = it },
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        PreviewStrip(
            running = running,
            novncRunning = novncRunning,
            onStartNovnc = onStartNovnc,
            viewOnly = viewOnly,
            onViewOnlyChange = onViewOnlyChange,
            onReload = { webView?.reload() },
            onFullscreen = { onFullscreenChange(true) },
            onReady = { webView = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        DashboardContent(
            onGoToRepair = onGoToRepair,
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
    }
}

/**
 * The 16:9 preview card with the compact viewer-control row directly under the
 * strip. Tapping the strip itself does not navigate anywhere.
 */
@Composable
private fun PreviewStrip(
    running: Int,
    novncRunning: Boolean,
    onStartNovnc: () -> Unit,
    viewOnly: Boolean,
    onViewOnlyChange: (Boolean) -> Unit,
    onReload: () -> Unit,
    onFullscreen: () -> Unit,
    onReady: (WebView) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // A 16:9 box; the height is derived from the measured width.
            val boxHeight = maxWidth * 9f / 16f
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(boxHeight),
            ) {
                if (novncRunning) {
                    NoVncView(
                        modifier = Modifier.fillMaxSize(),
                        viewOnly = viewOnly,
                        onReady = onReady,
                    )
                }
                if (!novncRunning) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Viewer not running",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = onStartNovnc) { Text("Start") }
                        }
                    }
                }
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HealthChip(running)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onFullscreen) { Text("Fullscreen") }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("View only", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(4.dp))
            Switch(checked = viewOnly, onCheckedChange = onViewOnlyChange)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onReload) { Text("Reload") }
            TextButton(onClick = onFullscreen) { Text("Fullscreen") }
        }
    }
}

/** The fullscreen viewer: only the desktop, with an Exit button and the View
 *  only switch in the top-right corner. */
@Composable
private fun FullscreenViewer(
    novncRunning: Boolean,
    onStartNovnc: () -> Unit,
    viewOnly: Boolean,
    onViewOnlyChange: (Boolean) -> Unit,
    onExit: () -> Unit,
    onReady: (WebView) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        if (novncRunning) {
            NoVncView(
                modifier = Modifier.fillMaxSize(),
                viewOnly = viewOnly,
                onReady = onReady,
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("The viewer is not running", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onStartNovnc) { Text("Start the viewer") }
                }
            }
        }
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(
                    MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("View only", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(4.dp))
            Switch(checked = viewOnly, onCheckedChange = onViewOnlyChange)
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onExit) { Text("Exit") }
        }
    }
}

@Composable
private fun HealthChip(running: Int) {
    val allRunning = running == Services.all.size
    Surface(
        shape = RoundedCornerShape(50),
        color = if (allRunning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
        contentColor = if (allRunning) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onTertiary,
    ) {
        Text(
            "$running of ${Services.all.size} running",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}