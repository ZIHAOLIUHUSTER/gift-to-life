package com.gift.tolife.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gift.tolife.core.database.dao.EntryDao
import com.gift.tolife.core.model.Entry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class OnThisDayState(
    val yearEntries: Map<Int, List<Entry>> = emptyMap(),
    val loading: Boolean = false
)

@HiltViewModel
class OnThisDayViewModel @Inject constructor(
    private val entryDao: EntryDao,
    private val repository: com.gift.tolife.core.database.EntryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OnThisDayState())
    val state: StateFlow<OnThisDayState> = _state.asStateFlow()

    private val _events = Channel<MemoryEntryEvent>(Channel.BUFFERED)
    internal val events = _events.receiveAsFlow()

    init { load() }

    fun load() {
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1

        val monthDay = "%02d-%02d".format(month, today)
        val entries = entryDao.getEntriesByMonthDay(monthDay)
        val result = entries.groupBy { entry ->
            Calendar.getInstance().apply { timeInMillis = entry.createdAt }.get(Calendar.YEAR)
        }
        _state.value = OnThisDayState(yearEntries = result)
    }

    fun deleteEntry(entryId: Long) {
        viewModelScope.launch {
            try {
                if (repository.softDelete(entryId)) {
                    // 本地移除后即可给撤销，不让后续读库失败吞掉成功回执。
                    _state.value = _state.value.copy(
                        yearEntries = _state.value.yearEntries
                            .mapValues { (_, entries) -> entries.filterNot { it.id == entryId } }
                            .filterValues { it.isNotEmpty() },
                    )
                    _events.send(MemoryEntryEvent.Deleted(entryId))
                } else {
                    _events.send(MemoryEntryEvent.Message("记录已经不在了"))
                }
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
                    refresh()
                } else {
                    _events.send(MemoryEntryEvent.Message("记录已无法恢复"))
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _events.send(MemoryEntryEvent.Message("恢复失败，请在回收站重试"))
            }
        }
    }
}