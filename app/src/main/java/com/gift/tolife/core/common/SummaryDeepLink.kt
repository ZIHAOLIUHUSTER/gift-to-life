package com.gift.tolife.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 通知/深链：点「上周的你，整理好了」直接进对应牌桌。
 * 与 [ReviewDeepLink]（随机回顾小组件）同构。
 */
object SummaryDeepLink {
    const val EXTRA_ENTRY_ID = "summary_entry_id"

    private val _entryId = MutableStateFlow<Long?>(null)
    val entryId: StateFlow<Long?> = _entryId.asStateFlow()

    fun open(entryId: Long) {
        _entryId.value = entryId
    }

    fun consume() {
        _entryId.value = null
    }
}
