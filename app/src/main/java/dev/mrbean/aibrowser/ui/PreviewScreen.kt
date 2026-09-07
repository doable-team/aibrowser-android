package dev.mrbean.aibrowser.ui

import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import dev.mrbean.aibrowser.AiBrowserApp
import dev.mrbean.aibrowser.engine.ServiceState
import dev.mrbean.aibrowser.service.ServiceController

/**
 * The live screen viewer: a WebView on noVNC's `vnc.html` loopback URL. The
 * WebView only ever loads 127.0.0.1 URLs; [fullscreen] hides the app's bottom
 * navigation through the MainScreen scaffold.
 */
@Composable
fun PreviewScreen(
    fullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val graph = remember { AiBrowserApp.graphOf(context) }
    val statuses by graph.supervisor.statuses.collectAsState()
    val novncRunning = statuses["novnc"]?.state is ServiceState.Running

    var viewOnly by remember { mutableStateOf(graph.config.load().viewOnly) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            Text("View only", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(4.dp))
            Switch(
                checked = viewOnly,
                onCheckedChange = { on ->
                    viewOnly = on
                    graph.config.save(graph.config.load().copy(viewOnly = on))
                },
            )
            TextButton(onClick = { webView?.reload() }) { Text("Reload") }
            TextButton(onClick = { onFullscreenChange(!fullscreen) }) {
                Text(if (fullscreen) "Exit fullscreen" else "Fullscreen")
            }
        }

        if (!novncRunning) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("The viewer is not running", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            ServiceController.start(context, ServiceController.ACTION_START, "novnc")
                        },
                    ) {
                        Text("Start the viewer")
                    }
                }
            }
        } else {
            NoVncView(
                modifier = Modifier.fillMaxSize(),
                viewOnly = viewOnly,
                onReady = { webView = it },
            )
        }
    }
}