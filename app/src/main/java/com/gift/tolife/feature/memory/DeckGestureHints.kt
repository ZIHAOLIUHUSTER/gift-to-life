package com.gift.tolife.feature.memory

import android.content.Context

/** 教学只在相应手势实际完成后隐藏，独立于用户设置和数据库。 */
internal data class DeckGestureHintState(
    val swiped: Boolean = false,
    val flipped: Boolean = false,
) {
    fun hint(canSwipe: Boolean, canFlip: Boolean): String? = listOfNotNull(
        "左右滑动翻卡".takeIf { canSwipe && !swiped },
        "点一下看背面".takeIf { canFlip && !flipped },
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

internal class DeckGestureHints(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("gift_ui_hints", Context.MODE_PRIVATE)

    fun read() = DeckGestureHintState(
        swiped = preferences.getBoolean("deck_swiped", false),
        flipped = preferences.getBoolean("deck_flipped", false),
    )

    fun recordSwipe() {
        preferences.edit().putBoolean("deck_swiped", true).apply()
    }

    fun recordFlip() {
        preferences.edit().putBoolean("deck_flipped", true).apply()
    }
}
