package dev.mrbean.aibrowser.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val AppShapes = Shapes(
    // Cards use MaterialTheme.shapes.medium, so 16 dp corners.
    medium = RoundedCornerShape(16.dp),
)

@Composable
fun AiBrowserTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Primary,
            onPrimary = OnPrimary,
            secondary = Secondary,
            onSecondary = OnSecondary,
            tertiary = Tertiary,
            onTertiary = OnTertiary,
            error = Error,
            onError = OnError,
            background = Background,
            onBackground = OnSurface,
            surface = Surface,
            onSurface = OnSurface,
            surfaceVariant = SurfaceVariant,
            onSurfaceVariant = OnSurfaceVariant,
            outline = Outline,
        ),
        shapes = AppShapes,
        content = content,
    )
}