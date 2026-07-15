package com.gift.tolife.navigation

import androidx.annotation.DrawableRes
import com.gift.tolife.R

sealed class Screen(
    val route: String,
    val label: String,
    @DrawableRes val selectedIconRes: Int,
    @DrawableRes val unselectedIconRes: Int
) {
    data object Memory : Screen(
        route = "memory",
        label = "回忆",
        selectedIconRes = R.drawable.ic_auto_awesome,
        unselectedIconRes = R.drawable.ic_auto_awesome
    )

    data object Record : Screen(
        route = "record",
        label = "记录",
        selectedIconRes = R.drawable.ic_edit_note,
        unselectedIconRes = R.drawable.ic_edit_note
    )

    data object Settings : Screen(
        route = "settings",
        label = "设置",
        selectedIconRes = R.drawable.ic_settings,
        unselectedIconRes = R.drawable.ic_settings
    )

    data object WeekSummary : Screen(
        route = "week_summary",
        label = "周总结",
        selectedIconRes = R.drawable.ic_auto_awesome,
        unselectedIconRes = R.drawable.ic_auto_awesome
    )

    data object MonthSummary : Screen(
        route = "month_summary",
        label = "月总结",
        selectedIconRes = R.drawable.ic_auto_awesome,
        unselectedIconRes = R.drawable.ic_auto_awesome
    )

    data object OnThisDay : Screen(
        route = "on_this_day",
        label = "那年今日",
        selectedIconRes = R.drawable.ic_auto_awesome,
        unselectedIconRes = R.drawable.ic_auto_awesome
    )
}

val bottomNavItems = listOf(Screen.Memory, Screen.Record, Screen.Settings)
