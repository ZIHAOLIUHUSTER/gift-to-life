package com.gift.tolife.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gift.tolife.core.database.EntryRepository
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.EntryType
import com.gift.tolife.core.model.TagType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RandomReviewState(
    val entry: Entry? = null,
    val tags: List<TagType> = emptyList(),
    val pendingPreview: Pair<Entry, List<TagType>>? = null
)

/** 删除完成后才提供撤销，避免 UI 提前报告成功。 */
internal sealed interface MemoryEntryEvent {
    data class Deleted(val entryId: Long) : MemoryEntryEvent
    data class Message(val text: String) : MemoryEntryEvent
}

@HiltViewModel
class RandomReviewViewModel @Inject constructor(
    private val repository: EntryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RandomReviewState())
    val state: StateFlow<RandomReviewState> = _state.asStateFlow()

    private val _events = Channel<MemoryEntryEvent>(Channel.BUFFERED)
    internal val events = _events.receiveAsFlow()

    init {
        fetchRandom()
    }

    fun fetchRandom() {
        viewModelScope.launch { refreshRandom() }
    }

    private suspend fun refreshRandom() {
        val random = repository.getRandomEntryWithTags()
        _state.update {
            it.copy(entry = random?.first, tags = random?.second.orEmpty())
        }
    }

    fun deleteEntry(entryId: Long) {
        viewModelScope.launch {
            try {
                if (!repository.softDelete(entryId)) {
                    _events.send(MemoryEntryEvent.Message("记录已经不在了"))
                    return@launch
                }
                if (_state.value.entry?.id == entryId) {
                    _state.update { it.copy(entry = null, tags = emptyList()) }
                    // 删除已成功：换卡是 best-effort，不能因读库异常丢掉撤销机会。
                    try {
                        refreshRandom()
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                    }
                }
                _events.send(MemoryEntryEvent.Deleted(entryId))
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _events.send(MemoryEntryEvent.Message("未能移至回收站，请重试"))
            }
        }
    }

    fun restoreEntry(entryId: Long) {
        viewModelScope.launch {
            try {
                if (repository.restoreEntry(entryId)) {
                    val entry = repository.getById(entryId)
                    if (entry != null && !entry.isDeleted && entry.type == EntryType.NORMAL) {
                        val tags = repository.getTags(entryId).map { it.tag }
                        _state.update { it.copy(entry = entry, tags = tags) }
                    }
                } else {
                    _events.send(MemoryEntryEvent.Message("记录已无法恢复"))
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _events.send(MemoryEntryEvent.Message("恢复失败，请在回收站重试"))
            }
        }
    }

    /** 小组件深链：按 ID 加载条目，请求展示详情预览（已删除/不存在则忽略） */
    fun showEntry(entryId: Long) {
        viewModelScope.launch {
            val entry = repository.getById(entryId) ?: return@launch
            if (entry.isDeleted || entry.type != EntryType.NORMAL) return@launch
            val tags = repository.getTags(entryId).map { it.tag }
            _state.update { it.copy(pendingPreview = entry to tags) }
        }
    }

    fun consumePendingPreview() {
        _state.update { it.copy(pendingPreview = null) }
    }
}
