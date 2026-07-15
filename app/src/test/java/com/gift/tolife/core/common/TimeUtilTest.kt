package com.gift.tolife.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.*

class TimeUtilTest {
    @Test
    fun previousWeekRange_monday() {
        // 2026-07-13 is Monday, previous week should be 07-06 to 07-12
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 13, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val (start, end) = TimeUtil.previousWeekRangeFor(cal)
        val startCal = Calendar.getInstance().apply { timeInMillis = start }
        val endCal = Calendar.getInstance().apply { timeInMillis = end }
        assertEquals(2026, startCal.get(Calendar.YEAR))
        assertEquals(Calendar.JULY, startCal.get(Calendar.MONTH))
        assertEquals(6, startCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, endCal.get(Calendar.MINUTE))
        assertEquals(12, endCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun previousMonthRange_january() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 15, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val (start, end) = TimeUtil.previousMonthRangeFor(cal)
        val startCal = Calendar.getInstance().apply { timeInMillis = start }
        val endCal = Calendar.getInstance().apply { timeInMillis = end }
        assertEquals(2025, startCal.get(Calendar.YEAR))
        assertEquals(Calendar.DECEMBER, startCal.get(Calendar.MONTH))
        assertEquals(1, startCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(31, endCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun currentWeekRange_sunday() {
        // Sunday should still be in current week (not next week)
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 12, 18, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val (start, end) = TimeUtil.currentWeekRangeFor(cal)
        val startCal = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(Calendar.MONDAY, startCal.get(Calendar.DAY_OF_WEEK))
        assertTrue(startCal.get(Calendar.DAY_OF_MONTH) <= 12)
        assertTrue(end - start <= 7L * 24 * 60 * 60 * 1000)
    }

    @Test
    fun previousWeekRange_sunday() {
        // Sunday 2026-07-12: previous week should be 06-29 to 07-05
        // (not 07-06 to 07-12 which is the current week)
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 12, 18, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val (start, end) = TimeUtil.previousWeekRangeFor(cal)
        val startCal = Calendar.getInstance().apply { timeInMillis = start }
        val endCal = Calendar.getInstance().apply { timeInMillis = end }
        assertEquals(2026, startCal.get(Calendar.YEAR))
        assertEquals(Calendar.JUNE, startCal.get(Calendar.MONTH))
        assertEquals(29, startCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(5, endCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.JULY, endCal.get(Calendar.MONTH))
    }

    @Test
    fun previousWeekRange_wednesday() {
        // Wednesday 2026-07-15: previous week should be 07-06 to 07-12
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 15, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val (start, end) = TimeUtil.previousWeekRangeFor(cal)
        val startCal = Calendar.getInstance().apply { timeInMillis = start }
        val endCal = Calendar.getInstance().apply { timeInMillis = end }
        assertEquals(Calendar.MONDAY, startCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(6, startCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(12, endCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun previousMonthRange_februaryLeapYear() {
        // 2026-03-01: previous month is Feb 2026 (not a leap year, 28 days)
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 1, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val (start, end) = TimeUtil.previousMonthRangeFor(cal)
        val startCal = Calendar.getInstance().apply { timeInMillis = start }
        val endCal = Calendar.getInstance().apply { timeInMillis = end }
        assertEquals(2026, startCal.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, startCal.get(Calendar.MONTH))
        assertEquals(1, startCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(28, endCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun previousMonthRange_marchAfterLeapFebruary() {
        // 2024-03-15: previous month is Feb 2024 (leap year, 29 days)
        val cal = Calendar.getInstance().apply {
            set(2024, Calendar.MARCH, 15, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val (start, end) = TimeUtil.previousMonthRangeFor(cal)
        val startCal = Calendar.getInstance().apply { timeInMillis = start }
        val endCal = Calendar.getInstance().apply { timeInMillis = end }
        assertEquals(2024, startCal.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, startCal.get(Calendar.MONTH))
        assertEquals(1, startCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(29, endCal.get(Calendar.DAY_OF_MONTH))
    }
}
