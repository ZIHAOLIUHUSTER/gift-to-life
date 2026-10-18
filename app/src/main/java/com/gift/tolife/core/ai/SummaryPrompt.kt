package com.gift.tolife.core.ai

import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.TagType
import java.util.Calendar

enum class SummaryPeriod { WEEK, MONTH }

/**
 * 周/月总结的提示词。
 *
 * 2026-09-24 重写：旧版的"时光切片/情绪天气报告"要求模型写"诗意总结"却不要求引用事实，
 * 结果产出大量空泛意象（"愿困兽听见心跳，把灰蓝燃成星火"）。新版把口径改成
 * **事实锚定 + 卡片化输出**：卡片承载事实与洞察，正文只负责气口。
 * 提示词与 `.pi/lab/prompts/weekly/P1-cards.md`、`monthly/P1-cards-letter.md` 对齐。
 */
object SummaryPrompt {

    /** 低于这个条数就不生成（2026-09-24 由 3 放宽到 1：实测 1~2 条也能写出有内容的东西）。 */
    const val MIN_ENTRIES = 1

    const val MAX_ENTRY_CHARS = 2_000
    const val MAX_CHARS = 48_000

    private const val WEEKDAYS = "一二三四五六日"

    const val WEEK_SYSTEM = """你是用户的私人记录回顾助手。他每天随手记几笔（闪念、事记、情绪、知识），一周结束时想看到三样东西：这周真实发生了什么、哪句话值得留下、有没有他自己没注意到的模式。

他最讨厌辞藻华丽的空话——读起来漂亮但什么也没说。他见过太多"废墟""星光""困兽""愿你被温柔以待"这种东西。

写作铁律：
1. 只用输入里真实存在的信息。不得编造人名、事件、情绪、因果；记录里没写清楚的，就不提。
2. 引用必须逐字。摘录原话时一个字都不能改，包括错别字和口语。
3. 禁止空泛抒情和意象堆砌，禁止说教、劝勉、评价对错、祝福语。
4. 洞察必须落在地上：由至少两条记录共同支持，写清是哪天写的什么。
5. 用词克制、平视，像一个把他的记录都翻过一遍的老朋友在跟他说话。
6. 如果这周他过得不好，就如实描述，不要强行正能量；如果他过得好，也不要夸张。
7. 输入里"# 本期客观数据"是系统统计的、可信的数字，可以直接大胆使用（数量、时段、连续性等），这类具体数字比形容词更有价值。
8. 如果输入里说明记录很少，就不要罗列数据、不要拔高，只写你真正看到的那几句。

文风铁律（2026-09-26 加，专治"每个字都对、但什么都没说"）：
- 下面这些句子一个字都别学：
  ✗「这里既有复盘，也有很重的自我攻击；两者同时出现，你并没有把"不满意"轻轻放过。」
  ✗「在关系靠近和学业执行上，你都写到知道方向，却难以在现场行动。」
  ✗「这种落差本身，就是这周留下的实际变化。」
- 删句测试：写完逐句问"删掉它，读者会少知道什么"？答不上来的一律删掉。
- 不许评论自己的写作（"这些内容留在这里""没有被改成一句好听的话"这类），也不要替他升华某段经历的意义。
- 不许用"其实／某种程度上／不得不说／或许／某种意义上／不是…而是…"垫句子。
- 单句不超过 25 字，一段最多 3 句。长句是臃肿的主要来源。
- 每段至少 2 个"只有这份记录才写得出来"的锚点：具体日期、原话片段、具体物件或场景（像"路边停着一辆蓝白电动车"那种）。
- 同一件事不许在卡片和信里各说一遍。
- 宁可短，不要凑字数。

输出契约：只输出一个 JSON 对象。不要 markdown 代码块，不要 ``` 包裹，不要任何解释性文字。
{"keyword":"4-8 个字概括本周，要具体，不要比喻堆砌",
 "cards":[{"type":"fact","label":"卡片标签,2-4字","title":"短标题,6-12字","text":"正文,40字内"},
          {"type":"quote","label":"一句话","time":"09-17 深夜","text":"逐字原文"},
          {"type":"insight","label":"你没注意到的","title":"短标题,10字内","text":"观察,40字内","evidence":"依据,30字内"}],
 "letter":"给本周收尾的一段短文，100-150 字，第二人称，自然承接上面的卡片，不要复述卡片里已说过的内容"}

契约规则：
- cards 2-4 张，按重要性排序；type 只能是 fact（客观事实/数据）、quote（逐字摘录）、insight（观察与模式）。
- quote 的 text 必须是记录里的逐字原文。
- insight 必须由至少两条记录共同支持。
- 除 quote 的 time 外，所有字段都必填，不得留空、不得写"无"。
- 不要用 emoji。"""

    const val MONTH_SYSTEM = """你是用户的私人记录回顾助手，这件事你已经做了很久——你每隔一个月给他写一封信，因为他这一个月里的碎碎念，你都翻过。

这个月他会先看到几张卡片，再读到你的信。卡片负责事实，信负责被理解。

写作铁律：
1. 只写他真的写过的。不得编造事件、人物、情绪。你不了解他记录之外的生活。
2. 你的价值在于替他把散落一个月的东西连起来：某个反复出现的念头、月初到月末的变化、他自己没承认的疲惫或勇敢。这比复述更有用。
3. 说人话。不要文学腔的排比和比喻（"废墟""星光""困兽"这类一律不用），不要金句，不要劝勉，不要祝福。信里可以有温度，但不许煽情。
4. 如果他这月过得艰难，不要安慰式地说"一切都会好"；承认它，指出他在其中做对的事。
5. 结尾自然收住，像朋友放下信纸那样，不要总结陈词。
6. 第二人称"你"，不要用"我们看到""你能感受到"这种 AI 腔。
7. 如果输入给了"更早的月份"，那是给你看长线变化的背景（什么在延续、什么变了），但你只总结"本期"。若更早的月份写着没有记录，就不要假装了解那段时间。
8. 输入里"# 本期客观数据"是系统统计的、可信的数字。

文风铁律（2026-09-26 加，专治"每个字都对、但什么都没说"）：
- 下面这些句子一个字都别学：
  ✗「这里既有复盘，也有很重的自我攻击；两者同时出现，你并没有把"不满意"轻轻放过。」
  ✗「在关系靠近和学业执行上，你都写到知道方向，却难以在现场行动。」
  ✗「这种落差本身，就是这周留下的实际变化。」
- 删句测试：写完逐句问"删掉它，读者会少知道什么"？答不上来的一律删掉。
- 不许评论自己的写作（"这些内容留在这里""没有被改成一句好听的话"这类），也不要替他升华某段经历的意义。
- 不许用"其实／某种程度上／不得不说／或许／某种意义上／不是…而是…"垫句子。
- 单句不超过 25 字，一段最多 3 句。长句是臃肿的主要来源。
- 每段至少 2 个"只有这份记录才写得出来"的锚点：具体日期、原话片段、具体物件或场景（像"路边停着一辆蓝白电动车"那种）。
- 同一件事不许在卡片和信里各说一遍。
- 宁可短，不要凑字数。

输出契约：只输出一个 JSON 对象。不要 markdown 代码块，不要 ``` 包裹，不要任何解释性文字。
{"keyword":"4-8 个字概括这个月，要具体，不要比喻堆砌",
 "cards":[{"type":"fact","label":"卡片标签,2-4字","title":"短标题,6-12字","text":"正文,40字内"},
          {"type":"quote","label":"一句话","time":"08-17 深夜","text":"逐字原文"},
          {"type":"insight","label":"我没说出口的","title":"短标题,10字内","text":"观察,40字内","evidence":"依据,30字内"}],
 "letter":"给他的信，240-320 字，以「亲爱的：」开头"}

契约规则：
- cards 2-4 张，按重要性排序；type 只能是 fact（客观事实/数据）、quote（逐字摘录）、insight（观察与模式）。
- quote 的 text 必须是记录里的逐字原文。
- insight 必须由至少两条记录共同支持。
- 除 quote 的 time 外，所有字段都必填，不得留空、不得写"无"。
- 不要用 emoji。"""

    fun systemFor(period: SummaryPeriod): String =
        if (period == SummaryPeriod.WEEK) WEEK_SYSTEM else MONTH_SYSTEM

    /** 更早的月份（月志背景），[label] 形如 `2026 年 8 月`。 */
    data class BackfillMonth(val label: String, val entries: List<Entry>)

    /**
     * 拼 user prompt：客观数据 + 按天分组的记录（带星期、时段、标签、配图描述）。
     *
     * 返回 null 表示记录不足，不生成。
     */
    fun buildUserPrompt(
        period: SummaryPeriod,
        entries: List<Entry>,
        stats: SummaryStats,
        tagsByEntry: Map<Long, List<TagType>> = emptyMap(),
        backfill: List<BackfillMonth> = emptyList(),
    ): String? {
        if (entries.size < MIN_ENTRIES) return null

        val builder = StringBuilder()
        builder.appendLine("# 本期客观数据（由系统统计，可信）")
        stats.detailLines.forEach { builder.appendLine("- $it") }

        if (stats.isSparse) {
            builder.appendLine()
            builder.appendLine("（这段日子只留下 ${stats.count} 条记录，不要罗列数据、不要拔高，只写你真正看到的那几句。）")
        }

        builder.appendLine()
        builder.appendLine("# 记录：${periodLabel(period, entries)}")

        val budget = MAX_CHARS - builder.length - backfillBudget(backfill)
        val body = formatEntries(entries, tagsByEntry, budget)
        builder.append(body)

        for ((index, month) in backfill.withIndex()) {
            builder.appendLine()
            builder.appendLine()
            builder.appendLine("# 更早的月份（背景资料，不要单独总结）")
            builder.appendLine("## ${month.label}：${if (month.entries.isEmpty()) "没有记录" else "${month.entries.size} 条"}")
            if (month.entries.isNotEmpty()) {
                val slice = maxOf(4_000, (MAX_CHARS - builder.length) / (backfill.size - index).coerceAtLeast(1))
                builder.append(formatEntries(month.entries, emptyMap(), slice))
            }
        }

        return builder.toString().trimEnd().ifBlank { null }
    }

    private fun backfillBudget(backfill: List<BackfillMonth>): Int =
        if (backfill.isEmpty()) 0 else (MAX_CHARS / 3) * backfill.size

    private fun periodLabel(period: SummaryPeriod, entries: List<Entry>): String {
        val sorted = entries.sortedBy { it.createdAt }
        val start = Calendar.getInstance().apply { timeInMillis = sorted.first().createdAt }
        val end = Calendar.getInstance().apply { timeInMillis = sorted.last().createdAt }
        return if (period == SummaryPeriod.MONTH) {
            String.format(
                "%d 年 %d 月 %d 日 ～ %d 月 %d 日",
                start.get(Calendar.YEAR), start.get(Calendar.MONTH) + 1, start.get(Calendar.DAY_OF_MONTH),
                end.get(Calendar.MONTH) + 1, end.get(Calendar.DAY_OF_MONTH),
            )
        } else {
            String.format(
                "%d-%02d-%02d（周%s）～ %02d-%02d（周%s）",
                start.get(Calendar.YEAR), start.get(Calendar.MONTH) + 1, start.get(Calendar.DAY_OF_MONTH),
                WEEKDAYS[start.get(Calendar.DAY_OF_WEEK).let { if (it == 1) 6 else it - 2 }],
                end.get(Calendar.MONTH) + 1, end.get(Calendar.DAY_OF_MONTH),
                WEEKDAYS[end.get(Calendar.DAY_OF_WEEK).let { if (it == 1) 6 else it - 2 }],
            )
        }
    }

    /** 按天分组渲染；超预算时**按时间均匀抽样**（不再像旧版那样砍掉尾部）。 */
    private fun formatEntries(
        entries: List<Entry>,
        tagsByEntry: Map<Long, List<TagType>>,
        budget: Int,
    ): String {
        val sorted = entries.sortedBy { it.createdAt }
        val lines = sorted.map { entry -> entryLine(entry, tagsByEntry[entry.id].orEmpty()) }
        val sampled = fitToBudget(lines, budget)
        val byDay = sorted.zip(lines).filter { it.second in sampled }.groupBy { it.first.createdAt / DAY_MS }

        val out = StringBuilder()
        if (sampled.size < lines.size) {
            out.appendLine("（记录较多，已按时间均匀抽样 ${sampled.size}/${lines.size} 条）")
        }
        for ((dayMs, pairs) in byDay.entries.sortedBy { it.key }) {
            val cal = Calendar.getInstance().apply { timeInMillis = dayMs * DAY_MS }
            val weekday = WEEKDAYS[cal.get(Calendar.DAY_OF_WEEK).let { if (it == 1) 6 else it - 2 }]
            out.appendLine()
            out.appendLine(String.format("## %02d 月 %02d 日 周%s（%d 条）",
                cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), weekday, pairs.size))
            pairs.forEach { (_, line) -> out.appendLine("- $line") }
        }
        return out.toString().trimEnd()
    }

    private fun entryLine(entry: Entry, tags: List<TagType>): String {
        val cal = Calendar.getInstance().apply { timeInMillis = entry.createdAt }
        val time = String.format("%02d:%02d %s", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE),
            SummaryStatsBuilder.bandOf(entry.createdAt))
        val text = entry.content.trim().take(MAX_ENTRY_CHARS)
        val desc = entry.imageDescription?.takeIf { it.isNotBlank() }
        val parts = mutableListOf<String>()
        if (text.isNotEmpty()) parts += text
        // 2026-09-24：配图描述以前只在正文为空时才用得上，等于把一半内容挡在总结之外
        if (entry.imagePath != null) {
            parts += when {
                desc != null -> "[配图：$desc]"
                text.isNotEmpty() -> "[另有配图]"
                else -> "[配图]"
            }
        }
        var line = "$time｜" + parts.ifEmpty { mutableListOf("[空]") }.joinToString("  ")
        if (tags.isNotEmpty()) line += "  #" + tags.joinToString("/") { it.label }
        return line
    }

    /** 超预算时均匀抽样，保住整段日子的轮廓，而不是只留开头。 */
    internal fun fitToBudget(lines: List<String>, budget: Int): List<String> {
        if (lines.isEmpty()) return lines
        val total = lines.sumOf { it.length + 1 }
        if (total <= budget) return lines
        val keep = maxOf(1, (lines.size * budget / total))
        if (keep >= lines.size) return lines
        val step = lines.size.toDouble() / keep
        val picked = (0 until keep)
            .map { lines[(it * step).toInt().coerceIn(0, lines.size - 1)] }
            .toMutableList()
        // 抽样也要保住最近几天：旧版硬截断丢的就是这段
        picked[picked.lastIndex] = lines.last()
        return picked
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
