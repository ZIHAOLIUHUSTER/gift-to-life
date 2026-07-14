package com.gift.tolife.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gift.tolife.feature.memory.MemoryScreen
import com.gift.tolife.feature.memory.MonthSummaryScreen
import com.gift.tolife.feature.memory.WeekSummaryScreen
import com.gift.tolife.feature.record.RecordScreen
import com.gift.tolife.feature.settings.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                bottomNavItems.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == screen.route
                    } == true

                    NavigationBarItem(
                        icon = {
                            Icon(
                                painter = painterResource(id = if (selected) screen.selectedIconRes else screen.unselectedIconRes),
                                contentDescription = screen.label
                            )
                        },
                        label = { Text(screen.label) },
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
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
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Record.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Memory.route) {
                MemoryScreen(
                    onNavigateToWeekSummary = { navController.navigate(Screen.WeekSummary.route) },
                    onNavigateToMonthSummary = { navController.navigate(Screen.MonthSummary.route) }
                )
            }
            composable(Screen.Record.route) { RecordScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
            composable(Screen.WeekSummary.route) {
                WeekSummaryScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.MonthSummary.route) {
                MonthSummaryScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
