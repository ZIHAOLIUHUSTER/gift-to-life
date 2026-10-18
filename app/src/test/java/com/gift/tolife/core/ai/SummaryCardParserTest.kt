package com.gift.tolife.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryCardParserTest {

    private val goodJson = """
        {"keyword":"白天立誓夜里崩",
         "cards":[
           {"type":"fact","label":"节奏","title":"一周写了 8 条","text":"集中在 5 天，4 条在深夜。"},
           {"type":"quote","label":"一句话","time":"09-14 上午","text":"我是自己的王，王是不会焦虑的。"},
           {"type":"insight","label":"你没注意到的","title":"白天立誓，深夜自责","text":"周一上午说不会焦虑，当晚就复盘焦虑。","evidence":"09-14 上午与当晚两条记录"}
         ],
         "letter":"这周你像在两头拉扯。"}
    """.trimIndent()

    @Test
    fun `解析标准输出`() {
        val deck = SummaryCardParser.parse(goodJson)
        assertNotNull(deck)
        deck!!
        assertEquals("白天立誓夜里崩", deck.keyword)
        assertEquals(3, deck.cards.size)
        assertEquals(SummaryCardType.QUOTE, deck.cards[1].type)
        assertEquals("09-14 上午", deck.cards[1].time)
        assertEquals(SummaryCardType.INSIGHT, deck.cards[2].type)
        assertEquals("09-14 上午与当晚两条记录", deck.cards[2].evidence)
        assertTrue(deck.isStructured)
    }

    @Test
    fun `容忍 markdown 围栏与前后噪声`() {
        val raw = "好的，这是结果：\n```json\n$goodJson\n```\n希望你喜欢。"
        val deck = SummaryCardParser.parse(raw)
        assertNotNull(deck)
        assertEquals(3, deck!!.cards.size)
    }

    @Test
    fun `非法 JSON 返回 null`() {
        assertNull(SummaryCardParser.parse("""{"keyword":"x","cards":["""))
        assertNull(SummaryCardParser.parse("这周还不错，没什么可说的。"))
        assertNull(SummaryCardParser.parse(""))
    }

    @Test
    fun `缺字段时用默认标签且不崩`() {
        val raw = """{"cards":[{"type":"unknown","text":"一句话。"}],"letter":"信"}"""
        val deck = SummaryCardParser.parse(raw)!!
        assertEquals("这段时间", deck.keyword)
        assertEquals(SummaryCardType.FACT, deck.cards[0].type)
        assertEquals("事实", deck.cards[0].label)
    }

    @Test
    fun `空 text 的卡片被丢弃`() {
        val raw = """{"keyword":"k","cards":[{"type":"fact","text":"  "},{"type":"fact","text":"有内容"}],"letter":""}"""
        val deck = SummaryCardParser.parse(raw)!!
        assertEquals(1, deck.cards.size)
        assertEquals("有内容", deck.cards[0].text)
    }

    @Test
    fun `旧版纯文本总结退化成段落卡片并清掉星号`() {
        val old = "**基调**：暮蓝色。\n\n碎片一：掠夺零食。\n\n碎片二：深夜散步。"
        val deck = SummaryCardParser.parseOrPlain(old)
        assertFalse(deck.isStructured)
        assertEquals(3, deck.cards.size)
        assertFalse(deck.cards[0].text.contains("**"))
        assertTrue(deck.cards[0].text.startsWith("基调"))
    }

    @Test
    fun `字段名飘了也认`() {
        // 实测某些模型会返回 content/date/summary 这套键名（SiliconFlow 上复现过）
        val raw = """
            {"keyword":"台风天",
             "cards":[{"type":"quote","date":"08-09 下午","content":"白海豚撞晕了我"},
                      {"content":"把计划改成了去拙政园","label":"事记"}],
             "summary":"这周你在苏州淋了一场台风。"}
        """.trimIndent()
        val deck = SummaryCardParser.parse(raw)!!
        assertEquals(2, deck.cards.size)
        assertEquals(SummaryCardType.QUOTE, deck.cards[0].type)
        assertEquals("08-09 下午", deck.cards[0].time)
        assertEquals("白海豚撞晕了我", deck.cards[0].text)
        assertEquals(SummaryCardType.FACT, deck.cards[1].type)
        assertTrue(deck.letter.startsWith("这周你在苏州"))
    }

    @Test
    fun `只有卡片没有信件也算成功`() {
        val raw = """{"keyword":"k","cards":[{"type":"fact","text":"有内容"}]}"""
        val deck = SummaryCardParser.parse(raw)!!
        assertEquals(1, deck.cards.size)
        assertEquals("", deck.letter)
    }

    @Test
    fun `形状不对时不报错 把文字捞出来当纯文本`() {
        // 模型完全没守契约：没有 cards/letter，只有一堆自定义键
        val raw = """{"keyword":"测试","cards":[{"date":"09 月 14 日","note":"模型自己发明的键"}]}"""
        assertNull(SummaryCardParser.parse(raw))          // 严格解析失败
        val deck = SummaryCardParser.parseOrPlain(raw)    // 但绝不会让用户看到"读不出来"
        assertTrue(deck.cards.isNotEmpty())
        val all = deck.cards.joinToString(" ") { it.text }
        assertFalse(all.contains("{"))
        assertTrue(all.contains("模型自己发明的键"))
    }

    @Test
    fun `纯文本兜底不会切出几十张卡`() {
        val long = (1..25).joinToString("\n") { "第 $it 段内容" }
        val deck = SummaryCardParser.asPlainDeck(long)
        assertTrue(deck.cards.size <= 6)
    }

    @Test
    fun `只拿到思考时能从里面捞出定稿`() {
        // 模型常在思考里先写草稿再写定稿，而且夹杂大量自言自语
        val reasoning = """
            We need output a JSON object only. Let's draft: {"keyword":"草稿","cards":[{"type":"fact","text":"草稿版"}]}
            maybe too long. Let me count and trim. Final answer: {"keyword":"定稿","cards":[{"type":"fact","label":"节奏","text":"一周写了 8 条"}],"letter":""}
        """.trimIndent()
        val deck = SummaryCardParser.parse(reasoning)
        assertEquals("定稿", deck?.keyword)
        assertEquals("一周写了 8 条", deck?.cards?.first()?.text)
    }

    @Test
    fun `字符串里的花括号不会骗过扫描`() {
        val raw = """{"keyword":"关掉的时候}","cards":[{"type":"fact","text":"x"}]}"""
        assertEquals(1, SummaryCardParser.jsonCandidates(raw).size)
        assertEquals("关掉的时候}", SummaryCardParser.parse(raw)?.keyword)
    }

    @Test
    fun `列表摘要不露出 JSON`() {
        val line = SummaryCardParser.displayTitle(goodJson)
        assertTrue(line.startsWith("白天立誓夜里崩"))
        assertFalse(line.contains("keyword"))
        assertFalse(line.contains("{"))
    }

    @Test
    fun `旧纯文本总结的列表摘要去掉星号`() {
        val line = SummaryCardParser.displayTitle("**基调**：暮蓝色，一半喧嚣一半孤寂。")
        assertFalse(line.contains("**"))
        assertTrue(line.startsWith("基调"))
    }

    @Test
    fun `结构化失败时不抛异常而是退化`() {
        val deck = SummaryCardParser.parseOrPlain("怀念那个下午。")
        assertFalse(deck.isStructured)
        assertEquals(1, deck.cards.size)
    }
}
