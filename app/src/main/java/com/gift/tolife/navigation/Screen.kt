package com.gift.tolife.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 页面路由与底栏图标。
 *
 * 2026-09-17：图标从"自制 vector drawable（方角）"统一为 **Material 圆角图标**
 * （`Icons.Rounded.*`，material-icons-extended），与系列设计语言的"圆角亲和"一致。
 */
sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    data object Memory : Screen(
        route = "memory",
        label = "回忆",
        icon = Icons.Rounded.History,
    )

    data object Record : Screen(
        route = "record",
        label = "记录",
        icon = Icons.Rounded.EditNote,
    )

    data object Settings : Screen(
        route = "settings",
        label = "设置",
        icon = Icons.Rounded.Settings,
    )

    data object WeekSummary : Screen(
        route = "week_summary",
        label = "周总结",
        icon = Icons.AutoMirrored.Rounded.EventNote,
    )

    data object MonthSummary : Screen(
        route = "month_summary",
        label = "月总结",
        icon = Icons.Rounded.CalendarMonth,
    )

    /** 总结牌桌（参数是总结条目 id）。 */
    data object SummaryDeck : Screen(
        route = "summary_deck/{entryId}",
        label = "回顾",
        icon = Icons.Rounded.History,
    ) {
        const val ROUTE = "summary_deck/{entryId}"
        fun route(entryId: Long) = "summary_deck/$entryId"
    }

    data object OnThisDay : Screen(
        route = "on_this_day",
        label = "那年今日",
        icon = Icons.Rounded.Today,
    )
}

val bottomNavItems = listOf(Screen.Memory, Screen.Record, Screen.Settings)
