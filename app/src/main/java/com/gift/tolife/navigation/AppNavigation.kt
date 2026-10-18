package com.gift.tolife.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavType
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gift.tolife.core.ai.SummaryPeriod
import com.gift.tolife.core.common.ReviewDeepLink
import com.gift.tolife.core.common.ShareReceiver
import com.gift.tolife.core.common.SummaryDeepLink
import com.gift.tolife.feature.memory.MemoryScreen
import com.gift.tolife.feature.memory.OnThisDayScreen
import com.gift.tolife.feature.memory.SummaryDeckScreen
import com.gift.tolife.feature.memory.SummaryListScreen
import com.gift.tolife.feature.record.RecordScreen
import com.gift.tolife.feature.settings.SettingsScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // 分享接入先回到输入器；不与记录页争抢内容事件。
    LaunchedEffect(Unit) {
        ShareReceiver.navigationRequests.collect { request ->
            if (request != null) {
                navController.navigate(Screen.Record.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
                ShareReceiver.consumeNavigationRequest(request)
            }
        }
    }

    // 随机回顾小组件深链：点击卡片 → 切到回忆页（详情预览由 MemoryScreen 消费）
    LaunchedEffect(Unit) {
        ReviewDeepLink.entryId.collect { id ->
            if (id != null) {
                navController.navigate(Screen.Memory.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    // 回顾通知深链：点通知 → 直接进对应牌桌
    LaunchedEffect(Unit) {
        SummaryDeepLink.entryId.collect { id ->
            if (id != null) {
                navController.navigate(Screen.SummaryDeck.route(id)) {
                    launchSingleTop = true
                }
                SummaryDeepLink.consume()
            }
        }
    }

    val pageEnter: EnterTransition = fadeIn(tween(220)) +
        slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { it / 8 }
    val pageExit: ExitTransition = fadeOut(tween(140)) +
        slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it / 14 }
    val pagePopExit: ExitTransition = fadeOut(tween(160)) +
        slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { it / 8 }
    val deckEnter: EnterTransition = fadeIn(tween(200)) +
        scaleIn(initialScale = 0.98f, animationSpec = tween(240, easing = FastOutSlowInEasing))

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            val route = currentDestination?.route
            val immersive = route == Screen.OnThisDay.route ||
                route == Screen.WeekSummary.route || route == Screen.MonthSummary.route ||
                route == Screen.SummaryDeck.ROUTE
            if (!immersive) {
                AppBottomBar(selectedRoute = route) { screen ->
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Record.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(180)) },
            popExitTransition = { fadeOut(tween(120)) },
        ) {
            composable(Screen.Memory.route) {
                MemoryScreen(
                    onNavigateToWeekSummary = { navController.navigate(Screen.WeekSummary.route) },
                    onNavigateToMonthSummary = { navController.navigate(Screen.MonthSummary.route) },
                    onOpenSummary = { id ->
                        navController.navigate(Screen.SummaryDeck.route(id)) { launchSingleTop = true }
                    },
                    onNavigateToOnThisDay = { navController.navigate(Screen.OnThisDay.route) },
                    onNavigateToRecord = {
                        navController.navigate(Screen.Record.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Screen.Record.route) { RecordScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
            composable(
                route = Screen.WeekSummary.route,
                enterTransition = { pageEnter },
                exitTransition = { pageExit },
                popEnterTransition = { fadeIn(tween(180)) },
                popExitTransition = { pagePopExit },
            ) {
                SummaryListScreen(
                    period = SummaryPeriod.WEEK,
                    onBack = { navController.popBackStack() },
                    onOpenDeck = { id -> navController.navigate(Screen.SummaryDeck.route(id)) },
                )
            }
            composable(
                route = Screen.MonthSummary.route,
                enterTransition = { pageEnter },
                exitTransition = { pageExit },
                popEnterTransition = { fadeIn(tween(180)) },
                popExitTransition = { pagePopExit },
            ) {
                SummaryListScreen(
                    period = SummaryPeriod.MONTH,
                    onBack = { navController.popBackStack() },
                    onOpenDeck = { id -> navController.navigate(Screen.SummaryDeck.route(id)) },
                )
            }
            composable(
                route = Screen.SummaryDeck.ROUTE,
                arguments = listOf(navArgument("entryId") { type = NavType.StringType }),
                enterTransition = { deckEnter },
                exitTransition = { pageExit },
                popEnterTransition = { fadeIn(tween(180)) },
                popExitTransition = { pagePopExit },
            ) {
                SummaryDeckScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Screen.OnThisDay.route,
                enterTransition = { pageEnter },
                exitTransition = { pageExit },
                popEnterTransition = { fadeIn(tween(180)) },
                popExitTransition = { pagePopExit },
            ) {
                OnThisDayScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
