package dev.mrbean.aibrowser.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    Setup("setup", "Setup", Icons.Filled.Home),
    Dashboard("dashboard", "Dashboard", Icons.Filled.Star),
    Preview("preview", "Preview", Icons.Filled.PlayArrow),
    Settings("settings", "Settings", Icons.Filled.Settings),
}

@Composable
fun AiBrowserRoot() {
    val navController = rememberNavController()
    // Preview can hide the bottom navigation through this state.
    var previewFullscreen by remember { mutableStateOf(false) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    LaunchedEffect(currentRoute) {
        if (currentRoute != Destination.Preview.route) previewFullscreen = false
    }

    Scaffold(
        bottomBar = {
            if (!previewFullscreen) {
                NavigationBar {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Setup.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.Setup.route) { SetupScreen() }
            composable(Destination.Dashboard.route) {
                DashboardScreen(
                    onGoToSetup = {
                        navController.navigate(Destination.Setup.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Destination.Preview.route) {
                PreviewScreen(
                    fullscreen = previewFullscreen,
                    onFullscreenChange = { previewFullscreen = it },
                )
            }
            composable(Destination.Settings.route) {
                SettingsScreen(
                    onGoToSetup = {
                        navController.navigate(Destination.Setup.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }
    }
}