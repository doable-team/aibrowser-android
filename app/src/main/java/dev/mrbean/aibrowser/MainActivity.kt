package dev.mrbean.aibrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.mrbean.aibrowser.ui.AiBrowserRoot
import dev.mrbean.aibrowser.ui.theme.AiBrowserTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiBrowserTheme {
                AiBrowserRoot()
            }
        }
    }

    companion object {
        /** Intent extra selecting the tab the notification tap opens. */
        const val EXTRA_TAB = "dev.mrbean.aibrowser.tab"
        const val TAB_DASHBOARD = "dashboard"
    }
}