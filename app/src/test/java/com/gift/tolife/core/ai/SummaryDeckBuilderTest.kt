package com.gift.tolife.core.ai

import com.gift.tolife.core.model.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SummaryDeckBuilderTest {

    private fun ts(day: Int, hour: Int): Long =
        Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun entry(id: Long, day: Int, hour: Int, text: String) =
        Entry(id = id, content = text, createdAt = ts(day, hour))

    private val entries = listOf(
        entry(1, 14, 10, "我是自己的王，王是不会焦虑，迷茫，恐惧和悲伤的。"),
        entry(2, 15, 21, "今天和九日去了珞珈山。"),
        entry(3, 20, 23, "奖学金的事把我掀翻了，我憎恨现在正在打字的人。"),
    )

    private fun deck(vararg cards: SummaryCard) =
        SummaryDeck(keyword = "深夜的自我审判", cards = cards.toList(), letter = "这周你写了不少。", isStructured = true)

    @Test
    fun `整副牌的顺序是封面 卡片 信`() {
        val items = SummaryDeckBuilder.build(
            deck = deck(
                SummaryCard(SummaryCardType.FACT, "节奏", "一周 3 条", "都在晚上。"),
                SummaryCard(SummaryCardType.QUOTE, "一句话", null, "我是自己的王，王是不会焦虑，迷茫，恐惧和悲伤的。"),
            ),
            entries = entries,
            stats = SummaryStatsBuilder.build(entries),
            periodLabel = "2026 年 9 月 14 日 ～ 9 月 20 日",
        )
        assertEquals(DeckItemKind.COVER, items.first().kind)
        assertEquals(DeckItemKind.LETTER, items.last().kind)
        assertEquals(2, items.count { it.kind == DeckItemKind.CARD })
    }

    @Test
    fun `引语能定位回原文 并给出前后现场`() {
        val quote = "我是自己的王，王是不会焦虑，迷茫，恐惧和悲伤的。"
        val items = SummaryDeckBuilder.build(
            deck = deck(SummaryCard(SummaryCardType.QUOTE, "一句话", null, quote)),
            entries = entries,
            stats = SummaryStatsBuilder.build(entries),
            periodLabel = "本周",
        )
        val card = items.first { it.kind == DeckItemKind.CARD }
        assertTrue(card.canFlip)
        assertEquals("那前后你还写了", card.backTitle)
        assertTrue(card.backItems.isNotEmpty())
        assertTrue(card.backItems.any { it.highlighted })
        assertEquals(true, card.quoteExact)
        // 时间从命中的那条记录里补上，模型没给也不会空着
        assertNotNull(card.time)
    }

    @Test
    fun `背面最多两条 其余只报个数 且原文清洗过`() {
        val marked = listOf(
            entry(1, 14, 10, "我是自己的王。"),
            entry(2, 15, 21, "## 标题残留\n\n今天和九日去了**珞珈山**。"),
            entry(3, 20, 23, "奖学金的事把我掀翻了。"),
        )
        val items = SummaryDeckBuilder.build(
            deck = deck(SummaryCard(SummaryCardType.QUOTE, "一句话", null, "今天和九日去了珞珈山。")),
            entries = marked,
            stats = SummaryStatsBuilder.build(marked),
            periodLabel = "本周",
        )
        val card = items.first { it.kind == DeckItemKind.CARD }
        // 上下文本来是 3 条（前一条、命中条、后一条），定尺卡片只放得下 2 条
        assertEquals(2, card.backItems.size)
        assertEquals(1, card.backMore)
        assertTrue(card.backItems.any { it.highlighted })
        assertTrue(card.backItems.none { it.text.contains("##") })
        assertTrue(card.backItems.none { it.text.contains("**") })
    }

    @Test
    fun `模型改写过引文时不假装有出处`() {
        val items = SummaryDeckBuilder.build(
            deck = deck(SummaryCard(SummaryCardType.QUOTE, "一句话", null, "我像笼子里的野兽一样狂怒，可我在拯救自己。")),
            entries = entries,
            stats = SummaryStatsBuilder.build(entries),
            periodLabel = "本周",
        )
        val card = items.first { it.kind == DeckItemKind.CARD }
        assertFalse(card.canFlip)
        assertEquals(false, card.quoteExact)
    }

    @Test
    fun `事实卡背面是本地统计 不含模型猜测`() {
        val items = SummaryDeckBuilder.build(
            deck = deck(SummaryCard(SummaryCardType.FACT, "节奏", "一周 3 条", "都在晚上。")),
            entries = entries,
            stats = SummaryStatsBuilder.build(entries),
            periodLabel = "本周",
        )
        val card = items.first { it.kind == DeckItemKind.CARD }
        assertTrue(card.canFlip)
        assertEquals("这几张数字怎么来的", card.backTitle)
        assertTrue(card.backText!!.contains("3 条记录"))
    }

    @Test
    fun `低密度周期的事实卡不翻面`() {
        val sparse = entries.take(2)
        val items = SummaryDeckBuilder.build(
            deck = deck(SummaryCard(SummaryCardType.FACT, "节奏", null, "就两条。")),
            entries = sparse,
            stats = SummaryStatsBuilder.build(sparse),
            periodLabel = "本周",
        )
        val card = items.first { it.kind == DeckItemKind.CARD }
        assertFalse(card.canFlip)
        assertTrue(items.first { it.kind == DeckItemKind.COVER }.badges.isEmpty())
    }

    @Test
    fun `洞察卡背面是依据`() {
        val items = SummaryDeckBuilder.build(
            deck = deck(SummaryCard(SummaryCardType.INSIGHT, "你没注意到的", "白天立誓", "观察", evidence = "09-14 与 09-20")),
            entries = entries,
            stats = SummaryStatsBuilder.build(entries),
            periodLabel = "本周",
        )
        val card = items.first { it.kind == DeckItemKind.CARD }
        assertEquals("这一句从哪来", card.backTitle)
        assertEquals("09-14 与 09-20", card.backText)
    }

    @Test
    fun `没有正文时不出信件卡`() {
        val items = SummaryDeckBuilder.build(
            deck = SummaryDeck("k", listOf(SummaryCard(SummaryCardType.FACT, "事实", null, "x")), "", true),
            entries = entries,
            stats = SummaryStatsBuilder.build(entries),
            periodLabel = "本周",
        )
        assertNull(items.firstOrNull { it.kind == DeckItemKind.LETTER })
    }
}
