package com.gift.tolife.core.ai

import com.gift.tolife.core.common.TimeUtil
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SummaryMonthContextTest {
    private fun month(zone: String, year: Int, month: Int): Pair<Long, Long> {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone(zone)).apply {
            clear()
            set(year, month - 1, 1, 0, 0, 0)
        }
        val start = calendar.timeInMillis
        calendar.add(Calendar.MONTH, 1)
        return start to calendar.timeInMillis - 1
    }

    @Test
    fun historicalShanghaiSeptemberKeepsAugustAndJulyAfterMovingToUtc() {
        val range = month("Asia/Shanghai", 2026, 9)
        val target = SummaryGenerationTarget(SummaryPeriod.MONTH, range.first, range.second,
            expectedEntryId = 7, zoneId = "UTC")
        assertEquals(listOf(month("Asia/Shanghai", 2026, 8), month("Asia/Shanghai", 2026, 7)),
            summaryBackgroundRanges(target))
    }

    @Test
    fun monthNameDoesNotMoveToAugustAfterSwitchingToUtc() {
        val range = month("Asia/Shanghai", 2026, 9)
        val originalZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals("2026 年 9 月", TimeUtil.monthLabelForRange(range.first, range.second))
        } finally {
            TimeZone.setDefault(originalZone)
        }
    }

    @Test
    fun newTaskUsesItsFrozenZone() {
        val range = month("Asia/Shanghai", 2026, 9)
        val target = SummaryGenerationTarget(SummaryPeriod.MONTH, range.first, range.second, zoneId = "Asia/Shanghai")
        assertEquals(listOf(month("Asia/Shanghai", 2026, 8), month("Asia/Shanghai", 2026, 7)),
            summaryBackgroundRanges(target))
    }

    @Test
    fun dstMonthKeepsTheCorrectEarlierBoundaries() {
        val range = month("America/New_York", 2026, 3)
        val target = SummaryGenerationTarget(SummaryPeriod.MONTH, range.first, range.second,
            expectedEntryId = 8, zoneId = "Asia/Shanghai")
        assertEquals(listOf(month("America/New_York", 2026, 2), month("America/New_York", 2026, 1)),
            summaryBackgroundRanges(target))
    }

    @Test
    fun ambiguousHistoricalDstDoesNotGuessAnIncorrectBackgroundRange() {
        val range = month("America/New_York", 2026, 1)
        val target = SummaryGenerationTarget(SummaryPeriod.MONTH, range.first, range.second,
            expectedEntryId = 9, zoneId = "UTC")
        // 一月边界也符合不采用 DST 的 UTC-5 时区，但十一月起点不同，只能确认十二月。
        assertEquals(listOf(month("America/New_York", 2025, 12)), summaryBackgroundRanges(target))
    }
}
