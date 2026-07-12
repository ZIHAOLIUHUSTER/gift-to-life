package com.gift.tolife.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gift.tolife.core.database.EntryRepository
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.EntryType
import com.gift.tolife.core.model.TagType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemoryUiState(
    val randomEntry: Entry? = null,
    val randomEntryTags: List<TagType> = emptyList(),
    val summaryEntries: List<Entry> = emptyList(),
    val isGeneratingSummary: Boolean = false
)

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val repository: EntryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    init {
        // 加载历史总结
        viewModelScope.launch {
            repository.getAllEntries().collect { allEntries ->
                val summaries = allEntries.filter { it.type == EntryType.SUMMARY }
                _uiState.update { it.copy(summaryEntries = summaries) }
            }
        }
        // 首次加载时抽取一条随机记录
        fetchRandomEntry()
    }

    fun fetchRandomEntry() {
        viewModelScope.launch {
            val allEntries = repository.getAllEntries().first()
            val normalEntries = allEntries.filter { it.type == EntryType.NORMAL }
            if (normalEntries.isNotEmpty()) {
                val random = normalEntries.random()
                val tags = repository.getTags(random.id).map { it.tag }
                _uiState.update { it.copy(randomEntry = random, randomEntryTags = tags) }
            }
        }
    }

    fun generateWeekSummary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingSummary = true) }
            // 阶段 7 实现
            _uiState.update { it.copy(isGeneratingSummary = false) }
        }
    }

    fun generateMonthSummary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingSummary = true) }
            // 阶段 7 实现
            _uiState.update { it.copy(isGeneratingSummary = false) }
        }
    }
}
