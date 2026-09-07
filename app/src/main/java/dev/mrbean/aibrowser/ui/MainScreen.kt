package dev.mrbean.aibrowser.ui

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.mrbean.aibrowser.AiBrowserApp
import dev.mrbean.aibrowser.MainActivity

private enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Filled.Home),
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
    // The Home viewer can fill the whole screen through this state, hiding the
    // top bar and the bottom navigation while the viewer is fullscreen.
    var viewerFullscreen by remember { mutableStateOf(false) }

    // The notification's tap intent selects the Home tab.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val activity = context as? ComponentActivity
        val intent = activity?.intent
        if (intent?.getStringExtra(MainActivity.EXTRA_TAB) == MainActivity.TAB_DASHBOARD) {
            navController.navigateToTab(Destination.Home.route)
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    LaunchedEffect(currentRoute) {
        if (currentRoute != Destination.Home.route) viewerFullscreen = false
    }
    val isTab = currentRoute in Destination.entries.map { it.route }
    val showBars = isTab && !viewerFullscreen
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        topBar = {
            if (showBars && !isLandscape) {
                TopAppBar(
                    title = {
                        val label = if (currentRoute == Destination.Home.route) {
                            "AiBrowser"
                        } else {
                            Destination.entries.firstOrNull { it.route == currentRoute }?.label ?: ""
                        }
                        Text(label)
                    },
                )
            }
        },
        bottomBar = {
            if (showBars && !isLandscape) {
                NavigationBar {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = { navController.navigateToTab(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Row(Modifier.fillMaxSize()) {
            if (isLandscape && showBars) {
                Box(
                    modifier = Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    AppNavigationRail(navController)
                }
            }
            AppNavHost(
                navController = navController,
                viewerFullscreen = viewerFullscreen,
                onFullscreenChange = { viewerFullscreen = it },
                onSetupCompleteChanged = onSetupCompleteChanged,
                modifier = Modifier
                    .weight(1f)
                    .padding(innerPadding),
            )
        }
    }
}

/**
 * The landscape navigation: a compact rail on the left edge holding the same
 * destinations as the bottom bar in portrait. It reads the back stack from the
 * [navController] on its own, so its selection tracks navigation without the
 * rail being rebuilt when the screen content recomposes.
 */
@Composable
private fun AppNavigationRail(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    NavigationRail(
        modifier = Modifier.wrapContentHeight(),
        windowInsets = NavigationRailDefaults.windowInsets,
    ) {
        Destination.entries.forEach { destination ->
            NavigationRailItem(
                selected = currentRoute == destination.route,
                onClick = { navController.navigateToTab(destination.route) },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) },
            )
        }
    }
}

/**
 * The tab graph, shared by the portrait (top bar + bottom bar) and the
 * landscape (rail) layouts so the destinations are declared once.
 */
@Composable
private fun AppNavHost(
    navController: NavHostController,
    viewerFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    onSetupCompleteChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Destination.Home.route,
        modifier = modifier,
    ) {
        composable(Destination.Home.route) {
            HomeScreen(
                fullscreen = viewerFullscreen,
                onFullscreenChange = onFullscreenChange,
                onGoToRepair = {
                    navController.navigate(REPAIR_ROUTE) { launchSingleTop = true }
                },
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

/** The tab navigation options, identical for the bottom bar and the rail. */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private const val REPAIR_ROUTE = "repair"