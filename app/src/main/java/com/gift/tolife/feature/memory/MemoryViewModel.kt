package com.gift.tolife.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gift.tolife.core.ai.SummaryPrompt
import com.gift.tolife.core.database.EntryRepository
import com.gift.tolife.core.datastore.SettingsDataStore
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.EntryType
import com.gift.tolife.core.model.TagType
import com.gift.tolife.core.network.AiClient
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

sealed class MemoryEvent {
    data class ShowMessage(val message: String) : MemoryEvent()
}

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val settingsDataStore: SettingsDataStore,
    private val aiClient: AiClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MemoryEvent>()
    val events: SharedFlow<MemoryEvent> = _events.asSharedFlow()

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
            try {
                val settings = settingsDataStore.settings.first()
                if (settings.apiKey.isBlank()) {
                    _events.emit(MemoryEvent.ShowMessage("请先在设置中配置 API Key"))
                    _uiState.update { it.copy(isGeneratingSummary = false) }
                    return@launch
                }

                val now = System.currentTimeMillis()
                val weekAgo = now - 7 * 24 * 60 * 60 * 1000L
                val entries = repository.getAllEntries().first()
                    .filter { it.type == EntryType.NORMAL && it.createdAt in weekAgo..now }

                if (entries.size < 3) {
                    _events.emit(MemoryEvent.ShowMessage("至少需要 3 条记录才能生成总结"))
                    _uiState.update { it.copy(isGeneratingSummary = false) }
                    return@launch
                }

                val prompt = SummaryPrompt.pickRandomWeekPrompt()
                val content = aiClient.chat(
                    model = settings.summaryModel,
                    systemPrompt = prompt,
                    userMessage = SummaryPrompt.buildUserPrompt(entries)
                )

                if (content != null) {
                    val summary = Entry(
                        content = content,
                        type = EntryType.SUMMARY,
                        summaryStart = weekAgo,
                        summaryEnd = now
                    )
                    repository.save(summary)
                    _events.emit(MemoryEvent.ShowMessage("周总结已生成"))
                } else {
                    _events.emit(MemoryEvent.ShowMessage("生成失败，请检查网络和模型配置"))
                }
            } catch (t: Throwable) {
                _events.emit(MemoryEvent.ShowMessage("生成失败: ${t.message ?: "未知错误"}"))
            } finally {
                _uiState.update { it.copy(isGeneratingSummary = false) }
            }
        }
    }

    fun generateMonthSummary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingSummary = true) }
            try {
                val settings = settingsDataStore.settings.first()
                if (settings.apiKey.isBlank()) {
                    _events.emit(MemoryEvent.ShowMessage("请先在设置中配置 API Key"))
                    _uiState.update { it.copy(isGeneratingSummary = false) }
                    return@launch
                }

                val now = System.currentTimeMillis()
                val monthAgo = now - 30 * 24 * 60 * 60 * 1000L
                val entries = repository.getAllEntries().first()
                    .filter { it.type == EntryType.NORMAL && it.createdAt in monthAgo..now }

                if (entries.size < 3) {
                    _events.emit(MemoryEvent.ShowMessage("至少需要 3 条记录才能生成总结"))
                    _uiState.update { it.copy(isGeneratingSummary = false) }
                    return@launch
                }

                val content = aiClient.chat(
                    model = settings.summaryModel,
                    systemPrompt = SummaryPrompt.MONTH_LETTER,
                    userMessage = SummaryPrompt.buildUserPrompt(entries)
                )

                if (content != null) {
                    val summary = Entry(
                        content = content,
                        type = EntryType.SUMMARY,
                        summaryStart = monthAgo,
                        summaryEnd = now
                    )
                    repository.save(summary)
                    _events.emit(MemoryEvent.ShowMessage("月总结已生成"))
                } else {
                    _events.emit(MemoryEvent.ShowMessage("生成失败，请检查网络和模型配置"))
                }
            } catch (t: Throwable) {
                _events.emit(MemoryEvent.ShowMessage("生成失败: ${t.message ?: "未知错误"}"))
            } finally {
                _uiState.update { it.copy(isGeneratingSummary = false) }
            }
        }
    }
}
