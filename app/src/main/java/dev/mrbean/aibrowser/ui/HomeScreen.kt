package dev.mrbean.aibrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mrbean.aibrowser.engine.Services
import dev.mrbean.aibrowser.engine.ServiceState
import dev.mrbean.aibrowser.service.ServiceController

/**
 * The Home tab: a live 16:9 preview strip of the desktop on top (view-only, the
 * whole 1280x720 desktop scaled down, with a health chip and an Open button),
 * then the service overview below. The strip scrolls away with the content.
 */
@Composable
fun HomeScreen(
    onOpenPreview: () -> Unit,
    onGoToRepair: () -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
) {
    val statuses by viewModel.statuses.collectAsState()
    val running = statuses.values.count { it.state is ServiceState.Running }
    val novncRunning = statuses["novnc"]?.state is ServiceState.Running
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        PreviewStrip(
            running = running,
            novncRunning = novncRunning,
            onStartNovnc = {
                ServiceController.start(context, ServiceController.ACTION_START, "novnc")
            },
            onOpenPreview = onOpenPreview,
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
 * The 16:9 preview card. Tapping anywhere on the strip (outside the Start and
 * Open buttons) opens the full Preview tab.
 */
@Composable
private fun PreviewStrip(
    running: Int,
    novncRunning: Boolean,
    onStartNovnc: () -> Unit,
    onOpenPreview: () -> Unit,
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
                        viewOnly = true,
                    )
                }
                // A transparent tap layer over the viewer so the whole strip
                // opens the Preview tab; the buttons above it still win.
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(onClick = onOpenPreview),
                )
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
                    TextButton(onClick = onOpenPreview) { Text("Open") }
                }
            }
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