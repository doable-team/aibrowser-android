package dev.mrbean.aibrowser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SetupScreen(viewModel: SetupViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Self-test", style = MaterialTheme.typography.titleMedium)
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