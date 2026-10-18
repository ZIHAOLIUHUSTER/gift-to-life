package com.gift.tolife.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {
    @Test
    fun emptyAndWhitespaceQueriesHaveNoTerms() {
        listOf("", " \t\r\n", "\u3000\u00a0").forEach {
            assertEquals(SearchText.Query(emptyList(), ""), SearchText.parse(it))
        }
    }

    @Test
    fun spacesTabsNewlinesAndUnicodeSpacesSeparateTerms() {
        val query = SearchText.parse("  打印机\tWiFi\n步骤\u3000地址\u00a0端口  ")
        assertEquals(listOf("打印机", "WiFi", "步骤", "地址", "端口"), query.terms)
        assertEquals("打印机 WiFi 步骤 地址 端口", query.phrase)
    }

    @Test
    fun repeatedTermsAreDeduplicatedWithAsciiCaseOnly() {
        val query = SearchText.parse("WiFi wifi 打印机 打印机 Ä ä")
        assertEquals(listOf("WiFi", "打印机", "Ä", "ä"), query.terms)
        assertEquals("WiFi wifi 打印机 打印机 Ä ä", query.phrase)
    }

    @Test
    fun unspacedChineseAndPunctuationStayLiteral() {
        val text = "打印机无线连接#2_%\\'"
        assertEquals(SearchText.Query(listOf(text), text), SearchText.parse(text))
    }

    @Test
    fun asciiFoldingPreservesUnicodeAndUtf16Offsets() {
        val text = "ABCz ÄİΣ中文😀"
        assertEquals("abcz ÄİΣ中文😀", SearchText.foldAscii(text))
        assertEquals(text.length, SearchText.foldAscii(text).length)
    }

    @Test
    fun likeMetacharactersAreEscapedInTheCorrectOrder() {
        assertEquals("%50\\%\\_C:\\\\资料'%", SearchText.likePattern("50%_C:\\资料'"))
        assertEquals("%打印机%", SearchText.likePattern("打印机"))
    }

    @Test
    fun sixtyFourTermsRemainIndependent() {
        val words = (1..64).map { "word$it" }
        assertEquals(words, SearchText.parse(words.joinToString(" ")).terms)
    }

    @Test
    fun excessiveTermsFallBackToWholeTextWithoutIgnoringAnyCondition() {
        val text = (1..65).joinToString("\t") { "word$it" }
        assertEquals(SearchText.Query(listOf(text), text), SearchText.parse(" $text "))
    }

    @Test
    fun leadingAndTrailingWhitespaceIsTrimmedForThePhrase() {
        assertEquals("打印机 WiFi", SearchText.parse("\u3000 打印机\t\tWiFi \u2028").phrase)
    }

    @Test
    fun npmAndBomLikeSpacesDoNotBecomeTerms() {
        assertTrue(SearchText.parse("\u0085\u00a0").terms.isEmpty())
        assertEquals(listOf("打印机"), SearchText.parse("\u0085打印机\u0085").terms)
    }

    @Test
    fun queriesUpToTheLimitAreSplitNormally() {
        val nearLimit = "打".repeat(8190) + " 印"
        val query = SearchText.parse(nearLimit)
        assertFalse(query.overflow)
        assertEquals(listOf("打".repeat(8190), "印"), query.terms)
    }

    @Test
    fun overflowIsReportedInsteadOfBuildingAHugePattern() {
        val query = SearchText.parse("a".repeat(SearchText.MAX_QUERY_LENGTH + 1))
        assertTrue(query.overflow)
        assertTrue(query.terms.isEmpty())
        assertTrue(query.phrase.isEmpty())
    }

    @Test
    fun overflowAlsoAppliesWhenTheQueryContainsWhitespace() {
        val query = SearchText.parse("a ".repeat(SearchText.MAX_QUERY_LENGTH / 2 + 1))
        assertTrue(query.overflow)
    }

    @Test
    fun repeatedWordsDoNotTriggerTheTermLimit() {
        assertEquals(listOf("WiFi"), SearchText.parse(List(200) { "WiFi" }.joinToString(" ")).terms)
    }

    @Test
    fun fallbackNeverProducesAnEmptyTerm() {
        assertTrue(SearchText.parse(" \n打印机\n ").terms.all { it.isNotEmpty() })
    }
}
