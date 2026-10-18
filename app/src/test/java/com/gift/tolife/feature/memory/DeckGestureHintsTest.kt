package com.gift.tolife.feature.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DeckGestureHintsTest {
    @Test
    fun `首次教学合成一行 不另堆翻面提示`() {
        val hint = DeckGestureHintState().hint(canSwipe = true, canFlip = true)!!
        assertEquals("左右滑动翻卡 · 点一下看背面", hint)
        assertFalse(hint.contains('\n'))
    }

    @Test
    fun `完成哪种手势就隐藏哪种教学`() {
        assertEquals("点一下看背面", DeckGestureHintState(swiped = true).hint(true, true))
        assertEquals("左右滑动翻卡", DeckGestureHintState(flipped = true).hint(true, true))
        assertNull(DeckGestureHintState(swiped = true, flipped = true).hint(true, true))
    }

    @Test
    fun `单张不可翻的牌不显示虚假操作`() {
        assertNull(DeckGestureHintState().hint(canSwipe = false, canFlip = false))
        assertEquals("点一下看背面", DeckGestureHintState().hint(false, true))
        assertEquals("左右滑动翻卡", DeckGestureHintState().hint(true, false))
    }

    @Test
    fun `暂时不可用的手势不会被视为已经学会`() {
        val state = DeckGestureHintState()
        assertNull(state.hint(false, false))
        assertEquals("左右滑动翻卡 · 点一下看背面", state.hint(true, true))
    }
}
