package com.gift.tolife.feature.memory

import com.gift.tolife.core.ai.ManualSummaryTask
import com.gift.tolife.core.ai.SummaryGenerationTarget
import com.gift.tolife.core.ai.SummaryPeriod
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryListPolicyTest {
    private val range = 100L to 200L
    private fun task(period: SummaryPeriod, start: Long = 100, end: Long = 200) =
        ManualSummaryTask("work", SummaryGenerationTarget(period, start, end), running = true)

    @Test
    fun currentRangeShowsItsOwnProgress() {
        val state = SummaryState(manualTasks = listOf(task(SummaryPeriod.WEEK)))
        assertTrue(summaryGenerationBusy(state, SummaryPeriod.WEEK, range))
    }

    @Test
    fun weeklyWorkDoesNotBlockTheMonthlyEntry() {
        val state = SummaryState(isGenerating = true, generatingPeriod = SummaryPeriod.WEEK,
            manualTasks = listOf(task(SummaryPeriod.WEEK)))
        assertFalse(summaryGenerationBusy(state, SummaryPeriod.MONTH, range))
    }

    @Test
    fun rewritingHistoryDoesNotBlockTheCurrentCycle() {
        val state = SummaryState(isGenerating = true, generatingPeriod = SummaryPeriod.WEEK,
            manualTasks = listOf(task(SummaryPeriod.WEEK, 10, 20)))
        assertFalse(summaryGenerationBusy(state, SummaryPeriod.WEEK, range))
    }

    @Test
    fun enqueueingShowsProgressBeforeWorkInfoArrives() {
        val state = SummaryState(isGenerating = true, generatingPeriod = SummaryPeriod.MONTH)
        assertTrue(summaryGenerationBusy(state, SummaryPeriod.MONTH, range))
        assertFalse(summaryGenerationBusy(state, SummaryPeriod.WEEK, range))
    }
}
