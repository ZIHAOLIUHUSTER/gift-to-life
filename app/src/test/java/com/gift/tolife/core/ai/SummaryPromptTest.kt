package com.gift.tolife.core.ai

import com.gift.tolife.core.model.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SummaryPromptTest {

    private fun ts(day: Int, hour: Int): Long =
        Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun entry(
        id: Long,
        day: Int,
        hour: Int,
        text: String,
        image: Boolean = false,
        desc: String? = if (image) "一只橘猫趴在窗台上" else null,
    ) = Entry(
        id = id,
        content = text,
        createdAt = ts(day, hour),
        imagePath = if (image) "/tmp/x.webp" else null,
        imageDescription = desc,
    )

    private fun entries(n: Int) = (1..n).map { entry(it.toLong(), 10 + it, 21, "第 $it 条记录") }

    @Test
    fun `少于一条记录不生成`() {
        val stats = SummaryStatsBuilder.build(emptyList())
        assertNull(SummaryPrompt.buildUserPrompt(SummaryPeriod.WEEK, emptyList(), stats))
    }

    @Test
    fun `一条记录也生成 并提示不要拔高`() {
        val list = entries(1)
        val stats = SummaryStatsBuilder.build(list)
        val prompt = SummaryPrompt.buildUserPrompt(SummaryPeriod.WEEK, list, stats)!!
        assertTrue(prompt.contains("只留下 1 条记录"))
        assertTrue(prompt.contains("不要罗列数据"))
    }

    @Test
    fun `按天分组并带星期和时段`() {
        val list = listOf(
            entry(1, 14, 10, "上午写的"),
            entry(2, 14, 23, "深夜写的"),
            entry(3, 15, 20, "第二天晚上"),
        )
        val stats = SummaryStatsBuilder.build(list)
        val prompt = SummaryPrompt.buildUserPrompt(SummaryPeriod.WEEK, list, stats)!!
        assertTrue(prompt.contains("# 本期客观数据"))
        assertTrue(prompt.contains("## 09 月 14 日 周"))
        assertTrue(prompt.contains("10:00 上午｜上午写的"))
        assertTrue(prompt.contains("23:00 深夜｜深夜写的"))
        assertTrue(prompt.contains("20:00 晚上｜第二天晚上"))
    }

    @Test
    fun `配图描述进提示词`() {
        val list = listOf(
            entry(1, 14, 10, "今天拍了张照", image = true),
            entry(2, 15, 10, "纯文字"),
            entry(3, 16, 10, "", image = true, desc = null),
        )
        val stats = SummaryStatsBuilder.build(list)
        val prompt = SummaryPrompt.buildUserPrompt(SummaryPeriod.WEEK, list, stats)!!
        assertTrue(prompt.contains("[配图：一只橘猫趴在窗台上]"))
        assertTrue(prompt.contains("[配图]"))
    }

    @Test
    fun `超预算时按时间均匀抽样而不是砍尾`() {
        val lines = (1..100).map { "第 $it 行的内容" }
        val kept = SummaryPrompt.fitToBudget(lines, 200)
        assertTrue(kept.size < lines.size)
        // 首尾都要保住：旧版硬截断会把最后几天的记录整体丢掉
        assertTrue(kept.first().contains("第 1 行"))
        assertTrue(kept.last().contains("第 100 行"))
    }

    @Test
    fun `月志带上更早的月份作为背景`() {
        val list = entries(3)
        val stats = SummaryStatsBuilder.build(list)
        val backfill = listOf(
            SummaryPrompt.BackfillMonth("2026 年 8 月", listOf(entry(9, 10, 21, "八月的记录"))),
            SummaryPrompt.BackfillMonth("2026 年 7 月", emptyList()),
        )
        val prompt = SummaryPrompt.buildUserPrompt(SummaryPeriod.MONTH, list, stats, emptyMap(), backfill)!!
        assertTrue(prompt.contains("# 更早的月份（背景资料，不要单独总结）"))
        assertTrue(prompt.contains("## 2026 年 8 月：1 条"))
        assertTrue(prompt.contains("## 2026 年 7 月：没有记录"))
        assertTrue(prompt.contains("八月的记录"))
    }

    @Test
    fun `周与月用不同的系统提示词`() {
        assertTrue(SummaryPrompt.systemFor(SummaryPeriod.WEEK).contains("一周"))
        assertTrue(SummaryPrompt.systemFor(SummaryPeriod.MONTH).contains("信"))
        assertFalse(SummaryPrompt.systemFor(SummaryPeriod.WEEK).contains("亲爱的："))
    }

    @Test
    fun `系统提示词带 JSON 契约与反空泛约束`() {
        val week = SummaryPrompt.systemFor(SummaryPeriod.WEEK)
        assertTrue(week.contains("\"cards\""))
        assertTrue(week.contains("禁止空泛抒情"))
        assertTrue(week.contains("引用必须逐字"))
    }
}
