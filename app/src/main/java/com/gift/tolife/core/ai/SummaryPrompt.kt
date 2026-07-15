package com.gift.tolife.core.ai

import com.gift.tolife.core.model.Entry
import kotlin.random.Random

object SummaryPrompt {
    const val WEEK_SLICE = "这是我过去一周的记录。请帮我做一份私人的\"时光切片\"：用一句话概括这段日子的基调（像调色盘选一个颜色），然后列出 2-3 个闪光的碎片（从记录中摘取或再创作），最后用一句诗意的总结收尾。控制在 150 字以内，格式自由。"

    const val WEEK_WEATHER = "请把我过去一周的记录当成个人气象数据来分析。用\"情绪天气报告\"的形式帮我回顾：天气概况（总体情绪基调）、局部阵雨（低落或沉思的瞬间）、放晴时刻（开心或平静的片段）、下周展望（从最近几条记录推测）。语言像一本私人手帐，150 字以内。"

    const val MONTH_LETTER = "假设你是一位每隔一月会给我写信的老友。你仔细读了我这月的闪念记录，发现了一些我自己可能都没察觉的细微变化——情绪上的、习惯上的、或者反复出现的念头。请用温暖、真诚的口吻给我写一封 200 字左右的短信。不追求全面，只求有一两句能触动我。以\"亲爱的：\"开头。"

    fun pickRandomWeekPrompt(): String {
        return if (Random.nextBoolean()) WEEK_SLICE else WEEK_WEATHER
    }

    private const val MAX_CHARACTERS = 48_000
    private const val MAX_ENTRY_CHARS = 2_000

    fun buildUserPrompt(entries: List<Entry>): String? {
        if (entries.size < 3) return null

        // 按时间排序（早→晚）
        val sorted = entries.sortedBy { it.createdAt }

        // 截断每条记录
        val formatted = sorted.map { entry ->
            val time = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(entry.createdAt))
            val text = entry.content.ifBlank { "[图片: ${entry.imageDescription ?: "无描述"}]" }
            "[$time] ${text.take(MAX_ENTRY_CHARS)}"
        }

        // 时间序拼接，按总预算截断
        // 策略：优先保留所有近期记录，超出预算时从最早开始丢弃
        val result = buildString {
            var charCount = 0
            for (line in formatted.reversed()) { // 从最新遍历
                if (charCount + line.length > MAX_CHARACTERS) break
                insert(0, "$line\n\n") // 插入到开头，保持时间序
                charCount += line.length + 2
            }
        }.trimEnd()

        return if (result.isBlank()) null else result
    }
}