package com.gift.tolife.feature.memory

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 定高牌框的字号分档：短句大、长文小，避免"短卡一片小字、长卡挤成砖头"。 */
class DeckTypographyTest {

    @Test
    fun `按字数分三档`() {
        assertEquals(BodyTier.LARGE, bodyTierOf(1))
        assertEquals(BodyTier.LARGE, bodyTierOf(BODY_TIER_SHORT))
        assertEquals(BodyTier.MEDIUM, bodyTierOf(BODY_TIER_SHORT + 1))
        assertEquals(BodyTier.MEDIUM, bodyTierOf(BODY_TIER_MEDIUM))
        assertEquals(BodyTier.SMALL, bodyTierOf(BODY_TIER_MEDIUM + 1))
        assertEquals(BodyTier.SMALL, bodyTierOf(400))
    }

    @Test
    fun `48dp全文动作计入内容预算 小牌仍至少一行`() {
        val style = TextStyle(fontSize = 15.sp, lineHeight = 24.sp)
        assertEquals(1, maxLinesFor(80.dp, style, hasTitle = true))
        assertTrue(maxLinesFor(520.dp, style, hasTitle = true) < maxLinesFor(520.dp, style, hasTitle = false))
    }

    @Test
    fun `系统放大字体时减少卡内行数 不改变牌框高度`() {
        val style = TextStyle(fontSize = 15.sp, lineHeight = 24.sp)
        val normal = maxLinesFor(400.dp, style, hasTitle = false)
        val enlarged = maxLinesFor(400.dp, style, hasTitle = false, fontScale = 1.5f)
        assertTrue(enlarged < normal)
        assertTrue(enlarged >= 1)
    }
}
