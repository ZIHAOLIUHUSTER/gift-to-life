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
    fun `区间可用于安全切片`() {
        val text = "今天去了图书馆"
        val range = TextMatch.ranges(text, "图").first()
        assertEquals("图", text.substring(range.first, range.last + 1))
    }
}
