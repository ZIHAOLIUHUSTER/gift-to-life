package com.gift.tolife.core.ai

import com.gift.tolife.core.model.Entry
import kotlin.random.Random

object SummaryPrompt {
    // 周报 A：时光切片
    const val WEEK_SLICE = "这是我过去一周的记录。请帮我做一份私人的\"时光切片\"：用一句话概括这段日子的基调（像调色盘选一个颜色），然后列出 2-3 个闪光的碎片（从记录中摘取或再创作），最后用一句诗意的总结收尾。控制在 150 字以内，格式自由。"

    // 周报 B：情绪天气
    const val WEEK_WEATHER = "请把我过去一周的记录当成个人气象数据来分析。用\"情绪天气报告\"的形式帮我回顾：天气概况（总体情绪基调）、局部阵雨（低落或沉思的瞬间）、放晴时刻（开心或平静的片段）、下周展望（从最近几条记录推测）。语言像一本私人手帐，150 字以内。"

    // 月报：老友来信
    const val MONTH_LETTER = "假设你是一位每隔一月会给我写信的老友。你仔细读了我这月的闪念记录，发现了一些我自己可能都没察觉的细微变化——情绪上的、习惯上的、或者反复出现的念头。请用温暖、真诚的口吻给我写一封 200 字左右的短信。不追求全面，只求有一两句能触动我。以\"亲爱的：\"开头。"

    fun pickRandomWeekPrompt(): String {
        return if (Random.nextBoolean()) WEEK_SLICE else WEEK_WEATHER
    }

    data class SummaryInputBudget(
        val maxCharacters: Int = 48_000,
        val maxEntryCharacters: Int = 2_000,
        val minimumEntries: Int = 3
    )

    private val budget = SummaryInputBudget()

    fun buildUserPrompt(entries: List<Entry>): String? {
        if (entries.size < budget.minimumEntries) return null

        // 截断每条记录
        val truncated = entries.map { entry ->
            val text = if (entry.content.isNotBlank()) {
                "[${formatEntryTime(entry.createdAt)}] ${entry.content}".take(budget.maxEntryCharacters)
            } else {
                "[${formatEntryTime(entry.createdAt)}] [图片: ${entry.imageDescription ?: "无描述"}]".take(budget.maxEntryCharacters)
            }
            text
        }

        // 近期优先 + 均匀采样
        val recentCount = (truncated.size * 0.5).toInt().coerceAtLeast(1)
        val sampled = mutableListOf<String>()
        // 取最近的一半
        truncated.takeLast(recentCount).forEach { sampled.add(it) }
        // 等距采样前半部分
        val remaining = budget.minimumEntries - recentCount
        val step = if (remaining > 0 && truncated.size > recentCount) {
            (truncated.size - recentCount) / remaining
        } else 0
        if (step > 0) {
            for (i in 0 until truncated.size - recentCount step step.coerceAtLeast(1)) {
                sampled.add(truncated[i])
                if (sampled.size >= budget.minimumEntries + recentCount) break
            }
        }

        // 按字符预算截断
        val result = buildString {
            var charCount = 0
            for (s in sampled.distinct().sortedBy { it }) {
                if (charCount + s.length > budget.maxCharacters) break
                appendLine(s)
                appendLine()
                charCount += s.length + 2
            }
        }.trimEnd()

        return if (result.isBlank()) null else result
    }

    private fun formatEntryTime(timestamp: Long): String {
        val fmt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        return fmt.format(java.util.Date(timestamp))
    }
}
