package com.gift.tolife.core.ai

import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.TagType
import java.util.Calendar

/**
 * 周期客观统计（封面数据徽章 + 事实卡背面）。
 *
 * 全部由本地计算，不含模型猜测——这是卡片"可信感"的来源。
 */
data class SummaryStats(
    val count: Int,
    val dayCount: Int,
    val longestStreak: Int,
    val busiestDayLabel: String?,
    val busiestDayCount: Int,
    val bandCounts: List<Pair<String, Int>>,
    val tagCounts: List<Pair<String, Int>>,
    val imageCount: Int,
    val spanDays: Int,
) {
    /** 记录太少时不摆数据徽章，改用短文卡（1~2 条时数据很难看）。 */
    val isSparse: Boolean get() = count <= 2

    val topBand: String? get() = bandCounts.firstOrNull()?.first

    /** 封面副标题：`28 条 · 14 天 · 下午最多` */
    val subtitle: String
        get() = buildList {
            if (count > 0) add("$count 条")
            if (dayCount > 0) add("$dayCount 天")
            topBand?.let { add("${it}最多") }
        }.joinToString(" · ")

    /** 封面数据徽章；低密度时返回空列表。 */
    val badges: List<String>
        get() = if (isSparse) emptyList() else buildList {
            add("$count 条记录")
            add("覆盖 $dayCount 天")
            if (longestStreak >= 2) add("最长连着记 $longestStreak 天")
            if (imageCount > 0) add("$imageCount 张配图")
            if (spanDays > dayCount + 2) add("跨度 $spanDays 天")
        }

    /** 事实卡背面：`这几张数字怎么来的` */
    val detailLines: List<String>
        get() = buildList {
            add("$count 条记录，跨 $spanDays 天，其中 $dayCount 天动过笔")
            if (dayCount >= 2) add("最长连续记录 $longestStreak 天")
            busiestDayLabel?.let { add("最活跃的一天：$it，$busiestDayCount 条") }
            if (bandCounts.isNotEmpty()) {
                add("记录时段：" + bandCounts.joinToString("、") { "${it.first} ${it.second}" })
            }
            if (tagCounts.isNotEmpty()) {
                add("标签：" + tagCounts.joinToString("、") { "${it.first} ${it.second}" })
            }
            if (imageCount > 0) add("配图 $imageCount 张")
        }
}

object SummaryStatsBuilder {

    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun build(entries: List<Entry>, tagsByEntry: Map<Long, List<TagType>> = emptyMap()): SummaryStats {
        if (entries.isEmpty()) {
            return SummaryStats(0, 0, 0, null, 0, emptyList(), emptyList(), 0, 0)
        }
        val sorted = entries.sortedBy { it.createdAt }
        val byDay = sorted.groupBy { it.createdAt / DAY_MS }
        val dayKeys = byDay.keys.sorted()

        var longest = 1
        var current = 1
        for (i in 1 until dayKeys.size) {
            current = if (dayKeys[i] - dayKeys[i - 1] == 1L) current + 1 else 1
            if (current > longest) longest = current
        }

        val busiest = byDay.maxByOrNull { it.value.size }
        val bandCounts = sorted.groupingBy { bandOf(it.createdAt) }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key to it.value }
        val tagCounts = sorted.flatMap { tagsByEntry[it.id].orEmpty() }
            .groupingBy { it.label }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key to it.value }

        val busiestLabel = busiest?.let { (day, list) ->
            val cal = Calendar.getInstance().apply { timeInMillis = list.first().createdAt }
            String.format("%d 月 %d 日", cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        }

        return SummaryStats(
            count = sorted.size,
            dayCount = byDay.size,
            longestStreak = longest,
            busiestDayLabel = busiestLabel,
            busiestDayCount = busiest?.value?.size ?: 0,
            bandCounts = bandCounts,
            tagCounts = tagCounts,
            imageCount = sorted.count { !it.imagePath.isNullOrBlank() },
            spanDays = ((dayKeys.last() - dayKeys.first()).toInt()) + 1,
        )
    }

    /** 时段划分与试验台 `.pi/lab/lab.py:time_band()` 保持一致。 */
    fun bandOf(tsMillis: Long): String {
        val hour = Calendar.getInstance().apply { timeInMillis = tsMillis }.get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 5 -> "凌晨"
            hour < 9 -> "早晨"
            hour < 12 -> "上午"
            hour < 14 -> "中午"
            hour < 18 -> "下午"
            hour < 22 -> "晚上"
            else -> "深夜"
        }
    }
}
