package com.gift.tolife.core.ai

import java.util.Calendar
import java.util.TimeZone

/** 历史条目未存时区：只采用与原始月边界一致、且背景范围没有歧义的月份。 */
internal fun summaryBackgroundRanges(target: SummaryGenerationTarget, count: Int = 2): List<Pair<Long, Long>> {
    if (target.period != SummaryPeriod.MONTH || target.end <= target.start || count <= 0) return emptyList()
    // 月中不会因旅行时区把九月误认成八月。
    val middle = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = target.start + (target.end - target.start) / 2
    }
    val year = middle.get(Calendar.YEAR)
    val month = middle.get(Calendar.MONTH)

    fun range(zone: TimeZone, monthsAgo: Int): Pair<Long, Long> {
        val calendar = Calendar.getInstance(zone).apply {
            clear()
            set(year, month, 1, 0, 0, 0)
            add(Calendar.MONTH, -monthsAgo)
        }
        val start = calendar.timeInMillis
        calendar.add(Calendar.MONTH, 1)
        return start to calendar.timeInMillis - 1
    }

    val zones = if (target.expectedEntryId == null) {
        listOf(TimeZone.getTimeZone(target.zoneId))
    } else {
        (TimeZone.getAvailableIDs().asList() + target.zoneId).distinct().map(TimeZone::getTimeZone)
    }.filter { range(it, 0) == (target.start to target.end) }
    if (zones.isEmpty()) return emptyList()

    // 相同本期边界不一定有相同历史 DST；不能随意猜一个时区混入错误记录。
    return (1..count).mapNotNull { monthsAgo ->
        zones.map { range(it, monthsAgo) }.distinct().singleOrNull()
    }
}
