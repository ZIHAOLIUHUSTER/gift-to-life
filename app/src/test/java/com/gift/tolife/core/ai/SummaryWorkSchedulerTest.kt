package com.gift.tolife.core.ai

import androidx.work.ExistingWorkPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SummaryWorkSchedulerTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun hoursBetween(from: Long, to: Long) = (to - from) / 3_600_000.0

    @Test
    fun `周一早上之前 排到当天九点`() {
        // 2026-09-14 是周一
        val now = at(2026, 9, 14, 7)
        val delay = SummaryWorkScheduler.delayUntilNext(SummaryPeriod.WEEK, now)
        assertEquals(2.0, hoursBetween(now, now + delay), 0.01)
    }

    @Test
    fun `周一过了九点 排到下周一`() {
        val now = at(2026, 9, 14, 10)
        val delay = SummaryWorkScheduler.delayUntilNext(SummaryPeriod.WEEK, now)
        assertEquals(7 * 24 - 1.0, hoursBetween(now, now + delay), 0.01)
    }

    @Test
    fun `周三排到下周一`() {
        // 2026-09-16 是周三
        val now = at(2026, 9, 16, 12)
        val delay = SummaryWorkScheduler.delayUntilNext(SummaryPeriod.WEEK, now)
        assertEquals(5 * 24 - 3.0, hoursBetween(now, now + delay), 0.01)
    }

    @Test
    fun `周日排到第二天周一`() {
        // 2026-09-20 是周日
        val now = at(2026, 9, 20, 23)
        val delay = SummaryWorkScheduler.delayUntilNext(SummaryPeriod.WEEK, now)
        assertEquals(10.0, hoursBetween(now, now + delay), 0.01)
    }

    @Test
    fun `月志排到下月一号九点`() {
        val now = at(2026, 9, 24, 23)
        val delay = SummaryWorkScheduler.delayUntilNext(SummaryPeriod.MONTH, now)
        assertEquals(6 * 24 + 10.0, hoursBetween(now, now + delay), 0.01)
    }

    @Test
    fun `十二月跨年`() {
        val now = at(2026, 12, 31, 23)
        val delay = SummaryWorkScheduler.delayUntilNext(SummaryPeriod.MONTH, now)
        assertEquals(10.0, hoursBetween(now, now + delay), 0.01)
    }

    @Test
    fun `月初九点前仍排当天不跳到下月`() {
        val now = at(2026, 9, 1, 8)
        assertEquals(at(2026, 9, 1, 9), now + SummaryWorkScheduler.delayUntilNext(SummaryPeriod.MONTH, now))
    }

    @Test
    fun `月初九点后排下月避免已结束任务即时循环`() {
        val now = at(2026, 9, 1, 9)
        assertEquals(at(2026, 10, 1, 9), now + SummaryWorkScheduler.delayUntilNext(SummaryPeriod.MONTH, now))
    }

    @Test
    fun `启动保留任务终态追加后继绝不REPLACE自身`() {
        assertEquals(ExistingWorkPolicy.KEEP, SummaryWorkScheduler.enqueuePolicy(successor = false))
        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, SummaryWorkScheduler.enqueuePolicy(successor = true))
        assertEquals(ExistingWorkPolicy.KEEP, ManualSummaryScheduler.enqueuePolicy())
    }

    @Test
    fun `延时永远为正`() {
        listOf(SummaryPeriod.WEEK, SummaryPeriod.MONTH).forEach { period ->
            assertTrue(SummaryWorkScheduler.delayUntilNext(period, System.currentTimeMillis()) > 0)
        }
    }
}
