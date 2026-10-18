package com.gift.tolife.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记录原文里的 markdown 痕迹不能露在卡片上
 * （2026-09-26 真机截图里出现过 `## 在不确定中学习靠近：…`）。
 */
class PlainTextTest {

    @Test
    fun `标题 加粗 引用 列表符都去掉`() {
        val raw = """
            ## 在不确定中学习靠近：关于恋爱焦虑

            今天梳理了**我在亲密关系中的焦虑**，以及这段“差一点谈成恋爱”的经历。

            - 第一点
            - 第二点
            > 引用一句
        """.trimIndent()
        val clean = PlainText.clean(raw)
        assertFalse(clean.contains("#"))
        assertFalse(clean.contains("**"))
        assertFalse(clean.contains("> "))
        assertFalse(clean.contains("\n- "))
        assertTrue(clean.startsWith("在不确定中学习靠近"))
        assertTrue(clean.contains("我在亲密关系中的焦虑"))
        assertTrue(clean.contains("第一点"))
    }

    @Test
    fun `连续空行压成一个空行`() {
        assertEquals("上\n\n下", PlainText.clean("上\n\n\n\n下"))
    }

    @Test
    fun `预览压掉换行并截断`() {
        val preview = PlainText.preview("第一行\n第二行", maxChars = 90)
        assertFalse(preview.contains("\n"))
        assertEquals("第一行 第二行", preview)

        val long = PlainText.preview("字".repeat(200), maxChars = 20)
        assertEquals(21, long.length) // 20 字 + 省略号
        assertTrue(long.endsWith("…"))
    }

    @Test
    fun `空文本不崩`() {
        assertEquals("", PlainText.clean("   \n  "))
        assertEquals("", PlainText.preview(""))
    }
}
