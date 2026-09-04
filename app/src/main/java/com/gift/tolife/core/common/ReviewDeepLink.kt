package com.gift.tolife.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 桌面小组件 → App 的单向深链通道（进程内单例，StateFlow 保证冷启动时也不丢消息）。
 * 随机回顾小组件点击时携带 entryId，App 收到后切到回忆页并直接预览该条目详情。
 */
object ReviewDeepLink {
    const val EXTRA_ENTRY_ID = "review_entry_id"

    private val _entryId = MutableStateFlow<Long?>(null)
    val entryId: StateFlow<Long?> = _entryId.asStateFlow()

    fun open(id: Long) {
        _entryId.value = id
    }

    fun consume() {
        _entryId.value = null
    }
}
