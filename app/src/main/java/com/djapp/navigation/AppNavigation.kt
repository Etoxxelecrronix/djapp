package com.djapp.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.djapp.i18n.Strings
import com.djapp.ui.screens.*

private data class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, Icons.Default.Home),
    BottomNavItem(Screen.FolderBrowser, Icons.Default.Folder),
    BottomNavItem(Screen.PlaylistManager, Icons.AutoMirrored.Filled.PlaylistPlay),
    BottomNavItem(Screen.Library, Icons.Default.LibraryMusic),
    BottomNavItem(Screen.SyncSettings, Icons.Default.Sync),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
    }

    Scaffold(
        topBar = {
            if (showBottomBar) {
                TopAppBar(
                    title = {
                        Text(
                            text = bottomNavItems.firstOrNull { item ->
                                currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
                            }?.screen?.title ?: Strings.t("home.title")
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == item.screen.route
                        } == true

                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.screen.title,
                                )
                            },
                            label = { Text(item.screen.title) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            ),
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Home.route) {
                HomePage(
                    onNavigateToUsbStick = { navController.navigate(Screen.UsbStick.route) },
                    onNavigateToFolders = { navController.navigate(Screen.FolderBrowser.route) },
                    onNavigateToAnalysis = { path -> navController.navigate(Screen.AnalysisProgress.createRoute(path)) },
                    onNavigateToLibrary = { navController.navigate(Screen.Library.route) },
                )
            }
            composable(Screen.FolderBrowser.route) {
                FolderBrowserPage(
                    onNavigateToAnalysis = { folderPath ->
                        navController.navigate(Screen.AnalysisProgress.createRoute(folderPath))
                    },
                )
            }
            composable(Screen.PlaylistManager.route) {
                PlaylistManagerPage(
                    onTrackClick = { trackId ->
                        navController.navigate(Screen.TrackDetail.createRoute(trackId))
                    },
                )
            }
            composable(
                route = Screen.AnalysisProgress.route,
                arguments = listOf(
                    navArgument("folderPath") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { backStackEntry ->
                val folderPath = backStackEntry.arguments?.getString("folderPath") ?: ""
                AnalysisProgressPage(folderPath = java.net.URLDecoder.decode(folderPath, "UTF-8"))
            }
            composable(
                route = Screen.TrackDetail.route,
                arguments = listOf(
                    navArgument("trackId") {
                        type = NavType.LongType
                    },
                ),
            ) { backStackEntry ->
                val trackId = backStackEntry.arguments?.getLong("trackId") ?: return@composable
                TrackDetailPage(
                    trackId = trackId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Screen.UsbStick.route) {
                UsbStickPage()
            }
            composable(Screen.Library.route) {
                LibraryPage(
                    onTrackClick = { trackId ->
                        navController.navigate(Screen.TrackDetail.createRoute(trackId))
                    },
                )
            }
            composable(Screen.SyncSettings.route) {
                SyncSettingsPage()
            }
        }
    }
}
