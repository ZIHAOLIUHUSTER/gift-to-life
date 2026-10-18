package com.gift.tolife.core.common

import java.util.*

object TimeUtil {
    fun currentWeekRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val daysSinceMonday = cal.get(Calendar.DAY_OF_WEEK).let { if (it == Calendar.SUNDAY) 6 else it - Calendar.MONDAY }
        cal.add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 6)
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return start to end
    }

    fun currentMonthRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return start to end
    }

    fun previousWeekRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val daysSinceMonday = cal.get(Calendar.DAY_OF_WEEK).let { if (it == Calendar.SUNDAY) 6 else it - Calendar.MONDAY }
        cal.add(Calendar.DAY_OF_MONTH, -daysSinceMonday - 7)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 6)
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return start to end
    }

    fun previousMonthRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -1)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return start to end
    }

    /**
     * 往前第 N 个自然月（1 = 上个月）。
     * 月志会取 1~3 个月作为背景资料，用来看出长线变化。
     */
    fun previousMonthRange(monthsAgo: Int): Pair<Long, Long> {
        if (monthsAgo <= 1) return previousMonthRange()
        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -(monthsAgo - 1)) }
        return previousMonthRangeFor(cal)
    }

    /** 形如 `2026 年 8 月`（用于把月份写进提示词）。 */
    fun monthLabel(startMillis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = startMillis }
        return String.format(
            "%d 年 %d 月",
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
        )
    }

    /** 完整月范围取月中定名，避免旅行后月初落在前一个月。 */
    fun monthLabelForRange(startMillis: Long, endMillis: Long): String =
        monthLabel(startMillis + (endMillis - startMillis) / 2)

    fun year(timestamp: Long): Int =
        Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.YEAR)

    fun previousWeekRangeFor(cal: Calendar): Pair<Long, Long> {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        val daysSinceMonday = c.get(Calendar.DAY_OF_WEEK).let { if (it == Calendar.SUNDAY) 6 else it - Calendar.MONDAY }
        c.add(Calendar.DAY_OF_MONTH, -daysSinceMonday - 7)
        val start = c.timeInMillis
        c.add(Calendar.DAY_OF_MONTH, 6)
        c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59); c.set(Calendar.SECOND, 59); c.set(Calendar.MILLISECOND, 999)
        val end = c.timeInMillis
        return start to end
    }

    fun previousMonthRangeFor(cal: Calendar): Pair<Long, Long> {
        val c = cal.clone() as Calendar
        c.add(Calendar.MONTH, -1)
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        val start = c.timeInMillis
        c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH))
        c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59); c.set(Calendar.SECOND, 59); c.set(Calendar.MILLISECOND, 999)
        val end = c.timeInMillis
        return start to end
    }

    fun currentWeekRangeFor(cal: Calendar): Pair<Long, Long> {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        val daysSinceMonday = c.get(Calendar.DAY_OF_WEEK).let { if (it == Calendar.SUNDAY) 6 else it - Calendar.MONDAY }
        c.add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
        val start = c.timeInMillis
        c.add(Calendar.DAY_OF_MONTH, 6)
        c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59); c.set(Calendar.SECOND, 59); c.set(Calendar.MILLISECOND, 999)
        val end = c.timeInMillis
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
