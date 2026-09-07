package dev.mrbean.aibrowser.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.mrbean.aibrowser.engine.ApiToken

/** A clipboard-copy lambda backed by the current context's clipboard manager. */
@Composable
fun rememberClipboardCopy(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        { text -> clipboard.setPrimaryClip(ClipData.newPlainText("aibrowser", text)) }
    }
}

/**
 * The tunnel token field: masked input with a Show/Hide toggle, Save, and an
 * optional Clear. Shared by Settings and the onboarding tunnel step.
 */
@Composable
fun TunnelTokenField(
    value: String,
    show: Boolean,
    onValueChange: (String) -> Unit,
    onToggleShow: () -> Unit,
    onSave: () -> Unit,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Tunnel token") },
            singleLine = true,
            visualTransformation = if (show) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.padding(top = 12.dp)) {
            TextButton(onClick = onToggleShow) {
                Text(if (show) "Hide" else "Show")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSave) { Text("Save") }
            if (onClear != null) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onClear) { Text("Clear") }
            }
        }
    }
}

/**
 * The API token list with its add row. [onRemove] is optional; the onboarding
 * shows the list without delete buttons.
 */
@Composable
fun ApiTokenList(
    tokens: List<ApiToken>,
    addLabel: String,
    onAddLabelChange: (String) -> Unit,
    onAdd: () -> Unit,
    addButtonLabel: String = "Add",
    onRemove: ((ApiToken) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        if (tokens.isEmpty()) {
            Text(
                "No tokens yet.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        tokens.forEach { token ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(token.label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        token.token.take(8),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (onRemove != null) {
                    IconButton(onClick = { onRemove(token) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove ${token.label}")
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 12.dp))
        Row(
            Modifier.padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = addLabel,
                onValueChange = onAddLabelChange,
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onAdd) { Text(addButtonLabel) }
        }
    }
}

/**
 * One row of the hostnames guide: `<name> -> <target>` with a Copy button
 * copying the target. An empty [name] (hostname not configured yet) shows just
 * the target.
 */
@Composable
fun GuideRow(name: String, target: String, copy: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (name.isEmpty()) target else "$name -> $target",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { copy(target) }) { Text("Copy") }
    }
}