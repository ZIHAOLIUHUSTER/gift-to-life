package com.gift.tolife.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMatchTest {

    @Test
    fun `空 query 或空文本不高亮`() {
        assertTrue(TextMatch.ranges("今天去了图书馆", "").isEmpty())
        assertTrue(TextMatch.ranges("", "图书").isEmpty())
        assertTrue(TextMatch.ranges("今天去了图书馆", "   ").isEmpty())
    }

    @Test
    fun `中文命中所有非重叠区间`() {
        val ranges = TextMatch.ranges("图书馆和图书馆长", "图书")
        assertEquals(2, ranges.size)
        assertEquals(0 until 2, ranges[0])
        assertEquals(4 until 6, ranges[1])
    }

    @Test
    fun `忽略英文大小写`() {
        val ranges = TextMatch.ranges("Android 和 android 都算", "ANDROID")
        assertEquals(2, ranges.size)
        assertEquals(0 until 7, ranges[0])
        assertEquals(10 until 17, ranges[1])
    }

    @Test
    fun `命中区间不重叠`() {
        // "aaaa" 里找 "aa"：只报 0-2 与 2-4，不报 1-3
        val ranges = TextMatch.ranges("aaaa", "aa")
        assertEquals(2, ranges.size)
        assertEquals(0 until 2, ranges[0])
        assertEquals(2 until 4, ranges[1])
    }

    @Test
    fun `query 长于文本时不越界`() {
        assertTrue(TextMatch.ranges("短", "很长的查询词").isEmpty())
    }

    @Test
    fun multipleWordsHighlightIndependentlyInTextOrder() {
        assertEquals(listOf(0 until 4, 7 until 10), TextMatch.ranges("WiFi连接到打印机", "打印机 wifi"))
    }

    @Test
    fun whitespaceAndDuplicatesDoNotCreateDuplicateHighlights() {
        assertEquals(listOf(0 until 3, 4 until 8), TextMatch.ranges("打印机 WiFi", " 打印机\tWiFi\n打印机 WIFI "))
    }

    @Test
    fun overlappingDifferentTermsMergeWithoutDuplicatingText() {
        assertEquals(listOf(0 until 4), TextMatch.ranges("打印机连接", "打印 打印机 机连"))
    }

    @Test
    fun unicodeCaseIsNotFoldedUnlikeAscii() {
        assertEquals(listOf(2 until 3), TextMatch.ranges("Ä ä", "ä"))
        assertTrue(TextMatch.ranges("Ä", "ä").isEmpty())
    }

    @Test
    fun sqlMetacharactersAreAlsoLiteralInHighlights() {
        assertEquals(listOf(2 until 4, 6 until 7), TextMatch.ranges("50%_C:\\资料", "%_ \\"))
    }

    @Test
    fun aLongQueryCanStillContainShortMatchingTerms() {
        assertEquals(listOf(0 until 3), TextMatch.ranges("打印机", "打印机 完全不存在的很长关键词"))
    }

    @Test
    fun blankSearchKeepsTheFullOriginalPreview() {
        val text = "前言\n".repeat(100) + "打印机步骤"
        assertEquals(text, TextMatch.preview(text, ""))
        assertEquals(text, TextMatch.preview(text, "\u3000 \n"))
    }

    @Test
    fun staleResultsWithoutAnyMatchKeepTheirOriginalContent() {
        assertEquals("今天去了图书馆", TextMatch.preview("今天去了图书馆", "打印机 wifi"))
    }

    @Test
    fun aMatchAtTheEndOfALongRecordIsVisibleWithLeadingEllipsis() {
        val text = "前言".repeat(100) + "打印机连接步骤"
        val preview = TextMatch.preview(text, "打印机")
        assertTrue(preview.startsWith("…"))
        assertTrue(preview.endsWith("打印机连接步骤"))
        assertTrue(TextMatch.ranges(preview, "打印机").first().first <= 25)
    }

    @Test
    fun aMatchAtTheBeginningKeepsTheStartAndMarksOmittedTail() {
        val preview = TextMatch.preview("打印机" + "无关正文".repeat(100), "打印机")
        assertTrue(preview.startsWith("打印机"))
        assertTrue(preview.endsWith("…"))
        assertEquals(161, preview.length)
    }

    @Test
    fun fullPhrasePreviewWinsOverAnEarlierScatteredWord() {
        val text = "打印机" + "前言".repeat(100) + "打印机 WiFi 连接方法"
        val preview = TextMatch.preview(text, "打印机 WiFi")
        assertTrue(preview.startsWith("…"))
        assertTrue(preview.contains("打印机 WiFi 连接方法"))
        assertEquals(2, TextMatch.ranges(preview, "打印机 WiFi").size)
    }

    @Test
    fun reverseWordOrderStillStartsNearTheEarliestHit() {
        val preview = TextMatch.preview("前言".repeat(100) + "WiFi连接到打印机", "打印机 WiFi")
        assertTrue(preview.endsWith("WiFi连接到打印机"))
        assertEquals(2, TextMatch.ranges(preview, "打印机 WiFi").size)
    }

    @Test
    fun previewCollapsesLineBreaksSoTheyCannotHideTheHit() {
        val text = "前言\n".repeat(100) + "\t打印机\nWiFi步骤\n"
        val preview = TextMatch.preview(text, "打印机 WiFi")
        assertTrue(preview.contains("打印机 WiFi步骤"))
        assertTrue(preview.none { it == '\n' || it == '\r' || it == '\t' })
        assertEquals(2, TextMatch.ranges(preview, "打印机 WiFi").size)
    }

    @Test
    fun previewDoesNotSplitEmojiAtEitherBoundary() {
        val text = "前".repeat(10) + "😀" + "打印机" + "后".repeat(20) + "😀" + "尾".repeat(100)
        val preview = TextMatch.preview(text, "打印机", maxChars = 25, contextBefore = 1)
        assertTrue(preview.startsWith("…😀打印机"))
        assertTrue(preview.endsWith("😀…"))
        assertTrue(preview.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8) == preview)
    }

    @Test
    fun shortSearchPreviewPreservesTextAndLiteralCharacters() {
        val text = "打印机 50%_C:\\资料"
        assertEquals(text, TextMatch.preview(text, "打印机 %_"))
    }

    @Test
    fun oversizedPreviewLimitDoesNotOverflow() {
        val text = "前".repeat(200) + "打印机"
        assertTrue(TextMatch.preview(text, "打印机", maxChars = Int.MAX_VALUE).endsWith("打印机"))
    }

    @Test
    fun previewCollapsesUnicodeLineSeparatorsSoTheyCannotHideTheHit() {
        val text = "前言\u2028".repeat(40) + "打印机\u2029WiFi"
        val preview = TextMatch.preview(text, "打印机 WiFi")
        assertTrue(preview.contains("打印机 WiFi"))
        assertTrue(preview.none { it == '\u2028' || it == '\u2029' })
        assertEquals(2, TextMatch.ranges(preview, "打印机 WiFi").size)
    }

    @Test
    fun hugeFallbackQueryStillHighlightsAfterPreviewCollapsesTabs() {
        // >64 词时 SearchText 回退为整段原文；预览把制表符折成空格，高亮必须同步归一。
        val text = (1..65).joinToString("\t") { "词$it" }
        val preview = TextMatch.preview(text, text)
        assertTrue(TextMatch.ranges(preview, text).isNotEmpty())
        assertTrue(preview.none { it == '\t' })
    }

    @Test
    fun `区间可用于安全切片`() {
        val text = "今天去了图书馆"
        val range = TextMatch.ranges(text, "图").first()
        assertEquals("图", text.substring(range.first, range.last + 1))
    }
}
