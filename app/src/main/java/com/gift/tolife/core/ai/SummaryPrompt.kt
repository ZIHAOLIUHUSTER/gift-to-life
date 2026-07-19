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

        val sorted = entries.sortedBy { it.createdAt }
        val days = sorted.map { it.createdAt / (24 * 60 * 60 * 1000) }.distinct().size
        val withImages = sorted.count { !it.imagePath.isNullOrBlank() }

        val statsLine = "本周期共 ${sorted.size} 条记录，覆盖 $days 天，其中 $withImages 条含图片。\n"

        val formatted = sorted.map { entry ->
            val time = com.gift.tolife.core.common.DateFormats.formatShortDateTime(entry.createdAt)
            val text = entry.content.ifBlank { "[图片: ${entry.imageDescription ?: "无描述"}]" }
            "[$time] ${text.take(MAX_ENTRY_CHARS)}"
        }

        val result = buildString {
            append(statsLine)
            append("\n")
            var charCount = statsLine.length + 1
            for (line in formatted) {
                if (charCount + line.length > MAX_CHARACTERS) break
                append(line)
                append("\n\n")
                charCount += line.length + 2
            }
        }.trimEnd()

        return if (result.isBlank()) null else result
    }
}