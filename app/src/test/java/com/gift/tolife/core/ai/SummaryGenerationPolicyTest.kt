package com.gift.tolife.core.ai

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SummaryGenerationPolicyTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 86_400_000L

    private fun at(year: Int, month: Int, day: Int, zone: TimeZone = utc): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, 0, 0, 0)
        }.timeInMillis

    private fun week(zone: TimeZone = utc): SummaryGenerationTarget = SummaryGenerationTarget(
        SummaryPeriod.WEEK, at(2026, 9, 14, zone), at(2026, 9, 21, zone) - 1, zoneId = zone.id,
    )

    @Test fun `completed natural week is valid`() {
        val target = week()
        assertTrue(target.isValid(target.end + 1))
    }

    @Test fun `unfinished range is invalid including its last millisecond`() {
        val target = week()
        assertFalse(target.isValid(target.end))
        assertFalse(target.isValid(target.start))
    }

    @Test fun `negative zero reversed and empty ranges are invalid`() {
        val target = week()
        val now = target.end + day
        assertFalse(target.copy(start = -1).isValid(now))
        assertFalse(target.copy(start = 0).isValid(now))
        assertFalse(target.copy(end = target.start).isValid(now))
        assertFalse(target.copy(end = target.start - 1).isValid(now))
        assertFalse(target.copy(expectedEntryId = 0).isValid(now))
        assertFalse(target.copy(expectedEntryId = -5).isValid(now))
    }

    @Test fun `new week must start on monday midnight and end exactly`() {
        val target = week()
        val now = target.end + 2 * day
        assertFalse(target.copy(start = target.start + 1).isValid(now))
        assertFalse(target.copy(start = target.start + day, end = target.end + day).isValid(now))
        assertFalse(target.copy(end = target.end - 1).isValid(now))
        assertFalse(target.copy(end = target.end + 1).isValid(now))
    }

    @Test fun `leap february and calendar month lengths are accepted`() {
        val leap = SummaryGenerationTarget(SummaryPeriod.MONTH, at(2024, 2, 1), at(2024, 3, 1) - 1, zoneId = "UTC")
        val short = SummaryGenerationTarget(SummaryPeriod.MONTH, at(2026, 2, 1), at(2026, 3, 1) - 1, zoneId = "UTC")
        val long = SummaryGenerationTarget(SummaryPeriod.MONTH, at(2026, 1, 1), at(2026, 2, 1) - 1, zoneId = "UTC")
        listOf(leap, short, long).forEach { assertTrue(it.isValid(at(2027, 1, 1))) }
        assertFalse(short.copy(start = short.start + day, end = short.end + day).isValid(at(2027, 1, 1)))
    }

    @Test fun `historical range stays valid after travelling and uses DB identity`() {
        val losAngeles = TimeZone.getTimeZone("America/Los_Angeles")
        val target = week(losAngeles).copy(expectedEntryId = 42)
        assertTrue(target.isValid(target.end + day, TimeZone.getTimeZone("Asia/Shanghai")))
        assertFalse(target.copy(expectedEntryId = null).isValid(target.end + day, TimeZone.getTimeZone("Asia/Shanghai")))
        assertTrue(SummaryGenerationPolicy.matchesStoredTarget(target, 42, target.start, target.end, true))
    }

    @Test fun `new requests freeze the origin timezone across default timezone changes`() {
        val original = TimeZone.getDefault()
        try {
            val losAngeles = TimeZone.getTimeZone("America/Los_Angeles")
            TimeZone.setDefault(losAngeles)
            val target = SummaryGenerationTarget(SummaryPeriod.WEEK,
                at(2026, 9, 14, losAngeles), at(2026, 9, 21, losAngeles) - 1)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals(losAngeles.id, target.zoneId)
            assertTrue(target.isValid(target.end + day))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test fun `spring DST week is 167 hours and autumn DST week is 169 hours`() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val spring = SummaryGenerationTarget(SummaryPeriod.WEEK,
            at(2026, 3, 2, zone), at(2026, 3, 9, zone) - 1, zoneId = zone.id)
        val autumn = SummaryGenerationTarget(SummaryPeriod.WEEK,
            at(2026, 10, 26, zone), at(2026, 11, 2, zone) - 1, zoneId = zone.id)
        assertEquals(167L, (spring.end - spring.start + 1) / 3_600_000)
        assertEquals(169L, (autumn.end - autumn.start + 1) / 3_600_000)
        listOf(spring, autumn).forEach {
            assertTrue(it.isValid(at(2027, 1, 1)))
            assertTrue(it.copy(expectedEntryId = 42).isValid(at(2027, 1, 1), utc))
        }
    }

    @Test fun `DST calendar months remain valid for new and historical requests`() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val march = SummaryGenerationTarget(SummaryPeriod.MONTH,
            at(2026, 3, 1, zone), at(2026, 4, 1, zone) - 1, zoneId = zone.id)
        val november = SummaryGenerationTarget(SummaryPeriod.MONTH,
            at(2026, 11, 1, zone), at(2026, 12, 1, zone) - 1, zoneId = zone.id)
        assertEquals(743L, (march.end - march.start + 1) / 3_600_000)
        assertEquals(721L, (november.end - november.start + 1) / 3_600_000)
        listOf(march, november).forEach {
            assertTrue(it.isValid(at(2027, 1, 1)))
            assertTrue(it.copy(expectedEntryId = 42).isValid(at(2027, 1, 1), utc))
        }
    }

    @Test fun `historical validation still rejects impossible durations and periods`() {
        val target = week().copy(expectedEntryId = 42)
        val now = at(2027, 1, 1)
        assertFalse(target.copy(end = target.start + day - 1).isValid(now))
        assertFalse(target.copy(end = target.start + 9 * day - 1).isValid(now))
        assertFalse(target.copy(period = SummaryPeriod.MONTH).isValid(now))
        assertFalse(target.copy(period = SummaryPeriod.MONTH, end = target.start + 33 * day - 1).isValid(now))
    }

    @Test fun `unknown zone cannot authorize new natural ranges`() {
        val target = week().copy(zoneId = "not-a-zone")
        assertFalse(target.isValid(target.end + day))
        // 历史条目的授权是数据库精确匹配，而非后来添加的时区字段。
        assertTrue(target.copy(expectedEntryId = 42).isValid(target.end + day))
    }

    @Test fun `unique name excludes original id version timezone and force intent`() {
        val target = week()
        assertEquals(target.uniqueName(), target.copy(expectedEntryId = 42, expectedUpdatedAt = 100).uniqueName())
        assertEquals(target.uniqueName(), target.copy(zoneId = "Asia/Tokyo").uniqueName())
        assertNotEquals(target.uniqueName(), target.copy(start = target.start - 7 * day, end = target.end - 7 * day).uniqueName())
        assertNotEquals(target.uniqueName(), target.copy(period = SummaryPeriod.MONTH).uniqueName())
    }

    @Test fun `overwrite requires explicit force and the selected original id`() {
        val target = week().copy(expectedEntryId = 42)
        assertTrue(SummaryGenerationPolicy.mayOverwrite(target, 42, true))
        assertFalse(SummaryGenerationPolicy.mayOverwrite(target, 42, false))
        assertFalse(SummaryGenerationPolicy.mayOverwrite(target, 43, true))
        assertFalse(SummaryGenerationPolicy.mayOverwrite(target, null, true))
        assertFalse(SummaryGenerationPolicy.mayOverwrite(target.copy(expectedEntryId = null), 42, true))
    }

    @Test fun `selected historical id range type and active state are authoritative`() {
        val target = week().copy(expectedEntryId = 42)
        assertTrue(SummaryGenerationPolicy.matchesStoredTarget(target, 42, target.start, target.end, true))
        assertFalse(SummaryGenerationPolicy.matchesStoredTarget(target, 43, target.start, target.end, true))
        assertFalse(SummaryGenerationPolicy.matchesStoredTarget(target, 42, target.start + 1, target.end, true))
        assertFalse(SummaryGenerationPolicy.matchesStoredTarget(target, 42, target.start, target.end - 1, true))
        assertFalse(SummaryGenerationPolicy.matchesStoredTarget(target, 42, target.start, target.end, false))
    }

    @Test fun `changed original version refuses stale or post-commit reruns`() {
        val target = week().copy(expectedEntryId = 42, expectedUpdatedAt = 100)
        assertTrue(SummaryGenerationPolicy.matchesStoredTarget(target, 42, target.start, target.end, true, 100))
        assertFalse(SummaryGenerationPolicy.matchesStoredTarget(target, 42, target.start, target.end, true, 101))
        assertFalse(target.copy(expectedEntryId = null).isValid(target.end + day))
        assertFalse(target.copy(expectedUpdatedAt = -1).isValid(target.end + day))
    }

    @Test fun `second lookup rejects deletion replacement and late duplicate`() {
        assertTrue(SummaryGenerationPolicy.originalStillMatches(null, null))
        assertTrue(SummaryGenerationPolicy.originalStillMatches(42, 42))
        assertFalse(SummaryGenerationPolicy.originalStillMatches(42, null))
        assertFalse(SummaryGenerationPolicy.originalStillMatches(42, 43))
        assertFalse(SummaryGenerationPolicy.originalStillMatches(null, 43))
    }

    @Test fun `unchanged scoped input and query ordering allow old result`() {
        val before = listOf(SummarySourceRevision(10, 0), SummarySourceRevision(11, 4))
        assertTrue(SummaryGenerationPolicy.sourceUnchanged(before, before.toList()))
        assertTrue(SummaryGenerationPolicy.sourceUnchanged(before, before.reversed()))
    }

    @Test fun `import replacing ids rejects network result even with same revisions`() {
        val before = listOf(SummarySourceRevision(10, 0), SummarySourceRevision(11, 4))
        val imported = listOf(SummarySourceRevision(20, 0), SummarySourceRevision(21, 4))
        assertFalse(SummaryGenerationPolicy.sourceUnchanged(before, imported))
    }

    @Test fun `editing a scoped entry revision rejects network result`() {
        val before = listOf(SummarySourceRevision(10, 0), SummarySourceRevision(11, 4))
        val edited = listOf(SummarySourceRevision(10, 0), SummarySourceRevision(11, 5))
        assertFalse(SummaryGenerationPolicy.sourceUnchanged(before, edited))
    }

    @Test fun `deletion restoration or additional scoped record reject network result`() {
        val before = listOf(SummarySourceRevision(10, 0), SummarySourceRevision(11, 4))
        assertFalse(SummaryGenerationPolicy.sourceUnchanged(before, before.take(1)))
        assertFalse(SummaryGenerationPolicy.sourceUnchanged(before, before + SummarySourceRevision(12, 0)))
        assertFalse(SummaryGenerationPolicy.sourceUnchanged(before, emptyList()))
    }

    @Test fun `writing today outside the closed range does not invalidate input`() {
        val target = week()
        val before = listOf(target.start to SummarySourceRevision(10, 0), target.end to SummarySourceRevision(11, 4))
        val after = before + (target.end + day to SummarySourceRevision(12, 0))
        fun scoped(records: List<Pair<Long, SummarySourceRevision>>) =
            records.filter { it.first in target.start..target.end }.map { it.second }
        assertTrue(SummaryGenerationPolicy.sourceUnchanged(scoped(before), scoped(after)))
    }

    @Test fun `only actively requested completed success opens deck`() {
        val task = ManualSummaryTask("a", week(), running = false, status = ManualSummaryStatus.SUCCESS, entryId = 42)
        assertTrue(SummaryGenerationPolicy.shouldOpen(task, "a"))
        assertFalse(SummaryGenerationPolicy.shouldOpen(task, null))
        assertFalse(SummaryGenerationPolicy.shouldOpen(task, "b"))
        assertFalse(SummaryGenerationPolicy.shouldOpen(task.copy(running = true), "a"))
        assertFalse(SummaryGenerationPolicy.shouldOpen(task.copy(entryId = null), "a"))
        assertFalse(SummaryGenerationPolicy.shouldOpen(task.copy(entryId = 0), "a"))
        assertFalse(SummaryGenerationPolicy.shouldOpen(task.copy(status = ManualSummaryStatus.SKIPPED), "a"))
        assertFalse(SummaryGenerationPolicy.shouldOpen(task.copy(status = ManualSummaryStatus.FAILED), "a"))
    }

    @Test fun `manual outcomes never schedule automatic API retries`() {
        assertEquals(SummaryWorkResult.SUCCESS, SummaryGenerationPolicy.manualResult(ManualSummaryStatus.SUCCESS))
        assertEquals(SummaryWorkResult.SUCCESS, SummaryGenerationPolicy.manualResult(ManualSummaryStatus.SKIPPED))
        assertEquals(SummaryWorkResult.FAILURE, SummaryGenerationPolicy.manualResult(ManualSummaryStatus.FAILED))
    }

    @Test fun `automatic retries are bounded and permanent failures are terminal`() {
        assertEquals(SummaryWorkResult.RETRY, SummaryGenerationPolicy.automaticResult(true, 0))
        assertEquals(SummaryWorkResult.RETRY, SummaryGenerationPolicy.automaticResult(true, 1))
        assertEquals(SummaryWorkResult.SUCCESS, SummaryGenerationPolicy.automaticResult(true, 2))
        assertEquals(SummaryWorkResult.SUCCESS, SummaryGenerationPolicy.automaticResult(true, 100))
        assertEquals(SummaryWorkResult.SUCCESS, SummaryGenerationPolicy.automaticResult(false, 0))
    }

    @Test fun `only terminal enabled automatic task schedules successor`() {
        assertTrue(SummaryGenerationPolicy.scheduleSuccessor(autoEnabled = true, retrying = false))
        assertFalse(SummaryGenerationPolicy.scheduleSuccessor(autoEnabled = true, retrying = true))
        assertFalse(SummaryGenerationPolicy.scheduleSuccessor(autoEnabled = false, retrying = false))
        assertFalse(SummaryGenerationPolicy.scheduleSuccessor(autoEnabled = false, retrying = true))
    }
}
