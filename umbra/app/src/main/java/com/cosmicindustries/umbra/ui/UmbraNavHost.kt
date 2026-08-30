package com.cosmicindustries.umbra.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.composable
import com.cosmicindustries.umbra.UmbraApp
import com.cosmicindustries.umbra.ui.apps.AppListScreen
import com.cosmicindustries.umbra.ui.apps.AppListViewModel
import com.cosmicindustries.umbra.ui.dashboard.DashboardScreen
import com.cosmicindustries.umbra.ui.dashboard.DashboardViewModel
import com.cosmicindustries.umbra.ui.dpi.ByeDpiSettingsScreen
import com.cosmicindustries.umbra.ui.dpi.ByeDpiSettingsViewModel
import com.cosmicindustries.umbra.ui.logs.LogsScreen
import com.cosmicindustries.umbra.ui.logs.LogsViewModel
import com.cosmicindustries.umbra.ui.settings.SettingsScreen
import com.cosmicindustries.umbra.ui.settings.SettingsViewModel
import com.cosmicindustries.umbra.ui.wireguard.WireGuardConfigScreen
import com.cosmicindustries.umbra.ui.wireguard.WireGuardConfigViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

private sealed class Destination(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Dashboard : Destination("dashboard", "Home", Icons.Filled.Home)
    object Apps : Destination("apps", "Apps", Icons.Filled.Apps)
    object WireGuard : Destination("wireguard", "WireGuard", Icons.Filled.VpnKey)
    object Dpi : Destination("dpi", "DPI Bypass", Icons.Filled.Shield)
    object Logs : Destination("logs", "Logs", Icons.Filled.History)
    object Settings : Destination("settings", "Settings", Icons.Filled.Settings)
}

private val bottomDestinations = listOf(
    Destination.Dashboard, Destination.Apps, Destination.WireGuard,
    Destination.Dpi, Destination.Logs, Destination.Settings,
)

@Composable
fun UmbraNavHost(app: UmbraApp, requestVpnConsent: (onGranted: () -> Unit) -> Unit) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                bottomDestinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Dashboard.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Dashboard.route) {
                val vm: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(app))
                DashboardScreen(vm, requestVpnConsent = requestVpnConsent)
            }
            composable(Destination.Apps.route) {
                val vm: AppListViewModel = viewModel(factory = AppListViewModel.factory(app))
                AppListScreen(vm)
            }
            composable(Destination.WireGuard.route) {
                val vm: WireGuardConfigViewModel = viewModel(factory = WireGuardConfigViewModel.factory(app))
                WireGuardConfigScreen(vm)
            }
            composable(Destination.Dpi.route) {
                val vm: ByeDpiSettingsViewModel = viewModel(factory = ByeDpiSettingsViewModel.factory(app))
                ByeDpiSettingsScreen(vm)
            }
            composable(Destination.Logs.route) {
                val vm: LogsViewModel = viewModel(factory = LogsViewModel.factory(app))
                LogsScreen(vm)
            }
            composable(Destination.Settings.route) {
                val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(app))
                SettingsScreen(vm)
            }
        }
    }
}
