package com.gift.tolife.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gift.tolife.core.ai.SummaryPrompt
import com.gift.tolife.core.common.TimeUtil
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
    val isGeneratingSummary: Boolean = false,
    val currentWeekSummary: Entry? = null,
    val currentMonthSummary: Entry? = null,
    val canGenerateWeek: Boolean = false,
    val canGenerateMonth: Boolean = false
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
        viewModelScope.launch {
            repository.getAllEntries().collect { allEntries ->
                val summaries = allEntries.filter { it.type == EntryType.SUMMARY }
                val (wStart, wEnd) = TimeUtil.currentWeekRange()
                val (mStart, mEnd) = TimeUtil.currentMonthRange()
                _uiState.update {
                    it.copy(
                        summaryEntries = summaries,
                        currentWeekSummary = summaries.find { s -> s.summaryStart == wStart && s.summaryEnd == wEnd },
                        currentMonthSummary = summaries.find { s -> s.summaryStart == mStart && s.summaryEnd == mEnd },
                        canGenerateWeek = TimeUtil.isMonday(),
                        canGenerateMonth = TimeUtil.isFirstDayOfMonth()
                    )
                }
            }
        }
        fetchRandomEntry()
    }

    fun fetchRandomEntry() {
        viewModelScope.launch {
            val allEntries = repository.getAllEntries().first()
            val normalEntries = allEntries.filter { it.type == EntryType.NORMAL && !it.isDeleted }
            if (normalEntries.isNotEmpty()) {
                val random = normalEntries.random()
                val tags = repository.getTags(random.id).map { it.tag }
                _uiState.update { it.copy(randomEntry = random, randomEntryTags = tags) }
            }
        }
    }

    fun generateWeekSummary() {
        if (!TimeUtil.isMonday()) {
            viewModelScope.launch { _events.emit(MemoryEvent.ShowMessage("请在周一生成本周总结")) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingSummary = true) }
            try {
                val settings = settingsDataStore.settings.first()
                if (settings.apiKey.isBlank()) {
                    _events.emit(MemoryEvent.ShowMessage("请先在设置中配置 API Key"))
                    _uiState.update { it.copy(isGeneratingSummary = false) }
                    return@launch
                }
                val (start, end) = TimeUtil.currentWeekRange()
                val entries = repository.getAllEntries().first()
                    .filter { it.type == EntryType.NORMAL && !it.isDeleted && it.createdAt in start..end }
                if (entries.size < 3) {
                    _events.emit(MemoryEvent.ShowMessage("本周记录不足 3 条，无法生成总结"))
                    _uiState.update { it.copy(isGeneratingSummary = false) }
                    return@launch
                }
                val prompt = SummaryPrompt.pickRandomWeekPrompt()
                val content = aiClient.chat(model = settings.summaryModel, systemPrompt = prompt, userMessage = SummaryPrompt.buildUserPrompt(entries))
                if (!content.isNullOrBlank()) {
                    val existing = _uiState.value.currentWeekSummary
                    if (existing != null) {
                        repository.update(existing.copy(content = content, summaryModel = settings.summaryModel))
                    } else {
                        repository.save(Entry(content = content, type = EntryType.SUMMARY, summaryStart = start, summaryEnd = end, summaryModel = settings.summaryModel))
                    }
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
        if (!TimeUtil.isFirstDayOfMonth()) {
            viewModelScope.launch { _events.emit(MemoryEvent.ShowMessage("请在每月1号生成本月总结")) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingSummary = true) }
            try {
                val settings = settingsDataStore.settings.first()
                if (settings.apiKey.isBlank()) {
                    _events.emit(MemoryEvent.ShowMessage("请先在设置中配置 API Key"))
                    _uiState.update { it.copy(isGeneratingSummary = false) }
                    return@launch
                }
                val (start, end) = TimeUtil.currentMonthRange()
                val entries = repository.getAllEntries().first()
                    .filter { it.type == EntryType.NORMAL && !it.isDeleted && it.createdAt in start..end }
                if (entries.size < 3) {
                    _events.emit(MemoryEvent.ShowMessage("本月记录不足 3 条，无法生成总结"))
                    _uiState.update { it.copy(isGeneratingSummary = false) }
                    return@launch
                }
                val content = aiClient.chat(model = settings.summaryModel, systemPrompt = SummaryPrompt.MONTH_LETTER, userMessage = SummaryPrompt.buildUserPrompt(entries))
                if (!content.isNullOrBlank()) {
                    val existing = _uiState.value.currentMonthSummary
                    if (existing != null) {
                        repository.update(existing.copy(content = content, summaryModel = settings.summaryModel))
                    } else {
                        repository.save(Entry(content = content, type = EntryType.SUMMARY, summaryStart = start, summaryEnd = end, summaryModel = settings.summaryModel))
                    }
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
