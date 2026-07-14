package com.gift.tolife.core.common

import java.util.*

object TimeUtil {
    fun isMonday(): Boolean = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY

    fun isFirstDayOfMonth(): Boolean = Calendar.getInstance().get(Calendar.DAY_OF_MONTH) == 1

    fun currentWeekRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_MONTH, -(cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY))
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 6)
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
        val end = cal.timeInMillis
        return start to end
    }

    fun currentMonthRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
        val end = cal.timeInMillis
        return start to end
    }

    fun formatWeek(start: Long, end: Long): String {
        val fmt = java.text.SimpleDateFormat("M/d", Locale.getDefault())
        return "${fmt.format(Date(start))} - ${fmt.format(Date(end))}"
    }

    fun formatMonth(start: Long, end: Long): String {
        val fmt = java.text.SimpleDateFormat("M月", Locale.getDefault())
        return fmt.format(Date(start))
    }
}
