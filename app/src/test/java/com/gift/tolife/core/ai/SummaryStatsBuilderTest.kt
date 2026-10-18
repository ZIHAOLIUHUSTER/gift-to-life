package com.gift.tolife.core.ai

import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.TagType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SummaryStatsBuilderTest {

    private fun ts(year: Int, month: Int, day: Int, hour: Int): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun entry(id: Long, at: Long, text: String = "记录", image: Boolean = false) = Entry(
        id = id,
        content = text,
        createdAt = at,
        imagePath = if (image) "/tmp/x.webp" else null,
    )

    @Test
    fun `统计条数 天数 连续天数 最活跃日`() {
        val entries = listOf(
            entry(1, ts(2026, 9, 14, 10)),
            entry(2, ts(2026, 9, 14, 22)),
            entry(3, ts(2026, 9, 15, 23), image = true),
            entry(4, ts(2026, 9, 17, 9)),
        )
        val stats = SummaryStatsBuilder.build(entries)
        assertEquals(4, stats.count)
        assertEquals(3, stats.dayCount)
        assertEquals(2, stats.longestStreak)
        assertEquals("9 月 14 日", stats.busiestDayLabel)
        assertEquals(2, stats.busiestDayCount)
        assertEquals(1, stats.imageCount)
        assertEquals(4, stats.spanDays)
        assertFalse(stats.isSparse)
    }

    @Test
    fun `时段划分与试验台一致`() {
        assertEquals("凌晨", SummaryStatsBuilder.bandOf(ts(2026, 9, 14, 3)))
        assertEquals("早晨", SummaryStatsBuilder.bandOf(ts(2026, 9, 14, 7)))
        assertEquals("上午", SummaryStatsBuilder.bandOf(ts(2026, 9, 14, 10)))
        assertEquals("中午", SummaryStatsBuilder.bandOf(ts(2026, 9, 14, 13)))
        assertEquals("下午", SummaryStatsBuilder.bandOf(ts(2026, 9, 14, 16)))
        assertEquals("晚上", SummaryStatsBuilder.bandOf(ts(2026, 9, 14, 20)))
        assertEquals("深夜", SummaryStatsBuilder.bandOf(ts(2026, 9, 14, 23)))
        assertEquals("凌晨", SummaryStatsBuilder.bandOf(ts(2026, 9, 14, 0)))
    }

    @Test
    fun `低密度周期不摆数据徽章`() {
        val sparse = SummaryStatsBuilder.build(listOf(entry(1, ts(2026, 9, 14, 10)), entry(2, ts(2026, 9, 15, 10))))
        assertTrue(sparse.isSparse)
        assertTrue(sparse.badges.isEmpty())
        assertTrue(sparse.subtitle.contains("2 条"))

        val normal = SummaryStatsBuilder.build((1..5).map { entry(it.toLong(), ts(2026, 9, 10 + it, 10)) })
        assertFalse(normal.isSparse)
        assertTrue(normal.badges.contains("5 条记录"))
    }

    @Test
    fun `标签分布按次数排序`() {
        val entries = (1..4).map { entry(it.toLong(), ts(2026, 9, 10 + it, 10)) }
        val tags = mapOf(
            1L to listOf(TagType.EMOTION),
            2L to listOf(TagType.EMOTION, TagType.EVENT),
            3L to listOf(TagType.EVENT),
            4L to listOf(TagType.EVENT),
        )
        val stats = SummaryStatsBuilder.build(entries, tags)
        assertEquals("事记" to 3, stats.tagCounts.first())
        assertTrue(stats.detailLines.any { it.startsWith("标签：") })
    }

    @Test
    fun `空周期不崩`() {
        val stats = SummaryStatsBuilder.build(emptyList())
        assertEquals(0, stats.count)
        assertEquals("", stats.subtitle)
        assertTrue(stats.badges.isEmpty())
        assertEquals(1, stats.detailLines.size)
    }
}
