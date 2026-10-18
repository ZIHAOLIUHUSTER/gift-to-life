package com.gift.tolife.core.ai

import com.gift.tolife.core.common.DateFormats
import com.gift.tolife.core.common.PlainText
import com.gift.tolife.core.model.Entry

enum class DeckItemKind { COVER, CARD, LETTER }

/** 背面「那前后你还写了」里的一条。 */
data class DeckBackItem(
    val time: String,
    val text: String,
    val highlighted: Boolean,
    val hasImage: Boolean,
)

/**
 * 渲染层直接可用的牌面。
 *
 * @param backTitle 背面标题（为 null 表示这张牌不可翻）
 */
data class DeckItem(
    val kind: DeckItemKind,
    val cardType: SummaryCardType? = null,
    val label: String = "",
    val title: String? = null,
    val text: String = "",
    val time: String? = null,
    val quoteExact: Boolean? = null,
    val badges: List<String> = emptyList(),
    val backTitle: String? = null,
    val backText: String? = null,
    val backItems: List<DeckBackItem> = emptyList(),
    /** 背面还有几条被截掉的前后记录。 */
    val backMore: Int = 0,
    val backFoot: String? = null,
) {
    val canFlip: Boolean get() = backTitle != null && (backText != null || backItems.isNotEmpty())
}

/**
 * 把「模型产出的牌组 + 本地记录 + 统计」拼成可渲染的整副牌。
 *
 * 背面不是装饰：引语卡翻过去是"那天的现场"，事实卡翻过去是"数字怎么算出来的"，
 * 洞察卡翻过去是"依据"——翻面 = 信任。
 */
object SummaryDeckBuilder {

    // 抽掉标点、空白与 markdown 痕迹：记录原文里的 ### / ** / > 不该妨碍引语定位
    private val NOISE = Regex("[\\s「」“”\"'，。！？、；：…—·\\-—,.!?;:()（）#*_>`~\\[\\]]+")

    private fun normalize(s: String) = NOISE.replace(s, "")

    /** 把模型引用的原话定位回真实条目（规范化后做包含匹配，取最短的候选）。 */
    fun locateSource(quote: String, entries: List<Entry>): Entry? {
        val q = normalize(quote)
        if (q.isEmpty()) return null
        val probe = if (q.length > 12) q.substring(0, 12) else q
        return entries
            .filter { it.content.isNotBlank() && normalize(it.content).contains(probe) }
            .minByOrNull { kotlin.math.abs(normalize(it.content).length - q.length) }
    }

    /** 定尺卡片放不下三条：背面最多两条，其余只报个数。 */
    private const val MAX_BACK_ITEMS = 2

    private fun contextOf(entry: Entry, entries: List<Entry>, window: Int = 1): List<DeckBackItem> {
        val index = entries.indexOf(entry)
        if (index < 0) return emptyList()
        val from = (index - window).coerceAtLeast(0)
        val to = (index + window).coerceAtMost(entries.size - 1)
        // 时间顺序保留（读起来是"前后"），超出的截掉最早的几条
        val slice = entries.subList(from, to + 1).takeLast(MAX_BACK_ITEMS)
        return slice.map { e ->
            DeckBackItem(
                time = DateFormats.formatShortDateTime(e.createdAt),
                text = PlainText.preview(e.content, maxChars = 90).ifBlank { "[配图]" },
                highlighted = e.id == entry.id,
                hasImage = !e.imagePath.isNullOrBlank(),
            )
        }
    }

    /** 被截掉的前后记录条数（在卡片页脚显示"还有 N 条"）。 */
    private fun droppedContextCount(entry: Entry, entries: List<Entry>, window: Int = 1): Int {
        val index = entries.indexOf(entry)
        if (index < 0) return 0
        val size = (index + window).coerceAtMost(entries.size - 1) - (index - window).coerceAtLeast(0) + 1
        return (size - MAX_BACK_ITEMS).coerceAtLeast(0)
    }

    fun build(
        deck: SummaryDeck,
        entries: List<Entry>,
        stats: SummaryStats,
        periodLabel: String,
        coverLabel: String = "本期关键词",
        letterLabel: String = "这周的信",
    ): List<DeckItem> {
        val sorted = entries.sortedBy { it.createdAt }
        val items = mutableListOf(
            DeckItem(
                kind = DeckItemKind.COVER,
                label = coverLabel,
                title = deck.keyword,
                text = periodLabel,
                badges = stats.badges,
            )
        )

        for (card in deck.cards) {
            val item = when (card.type) {
                SummaryCardType.FACT -> DeckItem(
                    kind = DeckItemKind.CARD,
                    cardType = card.type,
                    label = card.label,
                    title = card.title,
                    text = card.text,
                    time = card.time,
                    backTitle = if (stats.isSparse) null else "这几张数字怎么来的",
                    backText = if (stats.isSparse) null else stats.detailLines.joinToString("\n"),
                    backFoot = "系统统计 · 不含模型猜测",
                )

                SummaryCardType.QUOTE -> {
                    val source = locateSource(card.text, sorted)
                    val context = source?.let { contextOf(it, sorted) }.orEmpty()
                    val dropped = source?.let { droppedContextCount(it, sorted) } ?: 0
                    DeckItem(
                        kind = DeckItemKind.CARD,
                        cardType = card.type,
                        label = card.label,
                        title = card.title,
                        text = card.text,
                        time = card.time ?: source?.let { DateFormats.formatShortDateTime(it.createdAt) },
                        quoteExact = source != null,
                        backTitle = if (context.isEmpty()) null else "那前后你还写了",
                        backItems = context,
                        backMore = dropped,
                        backFoot = if (context.isEmpty()) null else "${context.first().time} 前后",
                    )
                }

                SummaryCardType.INSIGHT -> DeckItem(
                    kind = DeckItemKind.CARD,
                    cardType = card.type,
                    label = card.label,
                    title = card.title,
                    text = card.text,
                    time = card.time,
                    backTitle = card.evidence?.let { "这一句从哪来" },
                    backText = card.evidence,
                    backFoot = "来自你自己的记录",
                )

                SummaryCardType.PLAIN -> DeckItem(
                    kind = DeckItemKind.CARD,
                    cardType = card.type,
                    label = card.label,
                    title = card.title,
                    text = card.text,
                )
            }
            items += item
        }

        if (deck.letter.isNotBlank()) {
            items += DeckItem(kind = DeckItemKind.LETTER, label = letterLabel, text = deck.letter)
        }
        return items
    }
}
