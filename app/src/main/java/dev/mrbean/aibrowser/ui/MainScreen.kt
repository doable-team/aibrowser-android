package dev.mrbean.aibrowser.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.mrbean.aibrowser.AiBrowserApp
import dev.mrbean.aibrowser.MainActivity
import dev.mrbean.aibrowser.engine.Services
import dev.mrbean.aibrowser.engine.ServiceState

private enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Filled.Home),
    Preview("preview", "Preview", Icons.Filled.PlayArrow),
    Settings("settings", "Settings", Icons.Filled.Settings),
}

@Composable
fun AiBrowserRoot() {
    val context = LocalContext.current
    val graph = remember { AiBrowserApp.graphOf(context) }
    var setupComplete by remember { mutableStateOf(graph.config.load().setupComplete) }

    if (!setupComplete) {
        OnboardingScreen(
            onFinished = { setupComplete = true },
        )
        return
    }
    MainShell(onSetupCompleteChanged = { setupComplete = it })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(onSetupCompleteChanged: (Boolean) -> Unit) {
    val navController = rememberNavController()
    // Preview can hide the top and bottom bars through this state.
    var previewFullscreen by remember { mutableStateOf(false) }

    // The notification's tap intent selects the Home tab.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val activity = context as? ComponentActivity
        val intent = activity?.intent
        if (intent?.getStringExtra(MainActivity.EXTRA_TAB) == MainActivity.TAB_DASHBOARD) {
            navController.navigate(Destination.Home.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    LaunchedEffect(currentRoute) {
        if (currentRoute != Destination.Preview.route) previewFullscreen = false
    }
    val isTab = currentRoute in Destination.entries.map { it.route }

    val graph = remember { AiBrowserApp.graphOf(context) }
    val statuses by graph.supervisor.statuses.collectAsState()
    val running = statuses.values.count { it.state is ServiceState.Running }

    Scaffold(
        topBar = {
            if (isTab && !previewFullscreen) {
                TopAppBar(
                    title = {
                        Text(Destination.entries.firstOrNull { it.route == currentRoute }?.label ?: "")
                    },
                    actions = {
                        if (currentRoute == Destination.Home.route) {
                            HealthChip(running)
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (isTab && !previewFullscreen) {
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
            startDestination = Destination.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.Home.route) {
                DashboardScreen(
                    onGoToRepair = {
                        navController.navigate(REPAIR_ROUTE) { launchSingleTop = true }
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
                    onGoToRepair = {
                        navController.navigate(REPAIR_ROUTE) { launchSingleTop = true }
                    },
                )
            }
            composable(REPAIR_ROUTE) {
                RepairScreen(
                    onBack = { navController.popBackStack() },
                    onRunOnboardingAgain = { onSetupCompleteChanged(false) },
                )
            }
        }
    }
}

private const val REPAIR_ROUTE = "repair"

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