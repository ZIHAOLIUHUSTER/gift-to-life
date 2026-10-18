package com.gift.tolife.core.common

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 日期格式化统一入口（ThreadLocal 缓存，避免散落各处的 SimpleDateFormat 实例化）。
 *
 * 2026-09-27 视觉重构新增两个"给人看"的短格式：
 * - [formatDay]：`9月24日`（跨年补 `2024年9月24日`），用于随机回顾卡的元信息；
 * - [formatDayTime]：`9月24日 23:05`，用于记录列表的一行元信息。
 * 原 [formatDateTime] / [formatShortDateTime] 行为不变（AI、备份回执等仍在使用）。
 */
object DateFormats {
    private val dateTime = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    }
    private val shortDateTime = ThreadLocal.withInitial {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    }
    private val dayTime = ThreadLocal.withInitial {
        SimpleDateFormat("M月d日 HH:mm", Locale.getDefault())
    }
    private val dayTimeCrossYear = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.getDefault())
    }
    private val day = ThreadLocal.withInitial {
        SimpleDateFormat("M月d日", Locale.getDefault())
    }
    private val dayCrossYear = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy年M月d日", Locale.getDefault())
    }
    private val time = ThreadLocal.withInitial {
        SimpleDateFormat("HH:mm", Locale.getDefault())
    }
    private val monthYear = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy年M月", Locale.getDefault())
    }

    fun formatDateTime(ts: Long): String = dateTime.get()!!.format(Date(ts))

    fun formatShortDateTime(ts: Long): String = shortDateTime.get()!!.format(Date(ts))

    /** `9月24日 23:05`；不是今年则带年份。 */
    fun formatDayTime(ts: Long): String =
        if (isCurrentYear(ts)) dayTime.get()!!.format(Date(ts))
        else dayTimeCrossYear.get()!!.format(Date(ts))

    /** `9月24日`；不是今年则带年份。 */
    fun formatDay(ts: Long): String =
        if (isCurrentYear(ts)) day.get()!!.format(Date(ts))
        else dayCrossYear.get()!!.format(Date(ts))

    /** `23:05`；用于按天分组的记录流，日期交给分组标题。 */
    fun formatTime(ts: Long): String = time.get()!!.format(Date(ts))

    /** `2026年10月`；用于回忆页的时间锚点。 */
    fun formatMonthYear(ts: Long): String = monthYear.get()!!.format(Date(ts))

    private fun isCurrentYear(ts: Long): Boolean {
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        cal.timeInMillis = ts
        return cal.get(Calendar.YEAR) == currentYear
    }
}
