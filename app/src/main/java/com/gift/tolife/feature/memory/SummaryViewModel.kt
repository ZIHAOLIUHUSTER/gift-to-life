package com.gift.tolife.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gift.tolife.core.ai.SummaryPrompt
import com.gift.tolife.core.common.TimeUtil
import com.gift.tolife.core.database.EntryRepository
import com.gift.tolife.core.datastore.SettingsDataStore
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.EntryType
import com.gift.tolife.core.network.AiClient
import com.gift.tolife.core.ai.AiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SummaryState(
    val weekSummaries: List<Entry> = emptyList(),
    val monthSummaries: List<Entry> = emptyList(),
    val currentWeekSummary: Entry? = null,
    val currentMonthSummary: Entry? = null,
    val isGenerating: Boolean = false,
    val canGenerateWeek: Boolean = false,
    val canGenerateMonth: Boolean = false
)

sealed class SummaryEvent {
    data class ShowMessage(val message: String) : SummaryEvent()
}

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val settingsDataStore: SettingsDataStore,
    private val aiClient: AiClient
) : ViewModel() {

    private val _state = MutableStateFlow(SummaryState())
    val state: StateFlow<SummaryState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SummaryEvent>()
    val events: SharedFlow<SummaryEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.observeSummaries().collect { summaries ->
                val weeks = summaries.filter { it.summaryStart != null && it.summaryEnd != null && it.summaryEnd!! - it.summaryStart!! < 8L * 24 * 60 * 60 * 1000 }
                val months = summaries.filter { it.summaryStart != null && it.summaryEnd != null && it.summaryEnd!! - it.summaryStart!! > 25L * 24 * 60 * 60 * 1000 }
                val (wStart, wEnd) = TimeUtil.currentWeekRange()
                val (mStart, mEnd) = TimeUtil.currentMonthRange()
                _state.update {
                    it.copy(
                        weekSummaries = weeks,
                        monthSummaries = months,
                        currentWeekSummary = summaries.find { s -> s.summaryStart == wStart && s.summaryEnd == wEnd },
                        currentMonthSummary = summaries.find { s -> s.summaryStart == mStart && s.summaryEnd == mEnd },
                        canGenerateWeek = TimeUtil.isMonday(),
                        canGenerateMonth = TimeUtil.isFirstDayOfMonth()
                    )
                }
            }
        }
    }

    fun generateWeekSummary() {
        if (!TimeUtil.isMonday()) {
            viewModelScope.launch { _events.emit(SummaryEvent.ShowMessage("请在周一生成本周总结")) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true) }
            try {
                val settings = settingsDataStore.settings.first()
                if (settings.apiKey.isBlank()) {
                    _events.emit(SummaryEvent.ShowMessage("请先在设置中配置 API Key"))
                    _state.update { it.copy(isGenerating = false) }
                    return@launch
                }
                val (start, end) = TimeUtil.currentWeekRange()
                val entries = repository.getAllEntries().first()
                    .filter { it.type == EntryType.NORMAL && !it.isDeleted && it.createdAt in start..end }
                if (entries.size < 3) {
                    _events.emit(SummaryEvent.ShowMessage("本周记录不足 3 条"))
                    _state.update { it.copy(isGenerating = false) }
                    return@launch
                }
                val result = aiClient.chat(settings.summaryModel, SummaryPrompt.pickRandomWeekPrompt(), SummaryPrompt.buildUserPrompt(entries))
                if (result is AiResult.Success && result.value.isNotBlank()) {
                    val existing = _state.value.currentWeekSummary
                    if (existing != null) repository.update(existing.copy(content = result.value, summaryModel = settings.summaryModel))
                    else repository.save(Entry(content = result.value, type = EntryType.SUMMARY, summaryStart = start, summaryEnd = end, summaryModel = settings.summaryModel))
                    _events.emit(SummaryEvent.ShowMessage("周总结已生成"))
                } else {
                    _events.emit(SummaryEvent.ShowMessage("生成失败"))
                }
            } catch (t: Throwable) {
                _events.emit(SummaryEvent.ShowMessage("生成失败: ${t.message ?: ""}"))
            } finally {
                _state.update { it.copy(isGenerating = false) }
            }
        }
    }

    fun generateMonthSummary() {
        if (!TimeUtil.isFirstDayOfMonth()) {
            viewModelScope.launch { _events.emit(SummaryEvent.ShowMessage("请在每月1号生成本月总结")) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true) }
            try {
                val settings = settingsDataStore.settings.first()
                if (settings.apiKey.isBlank()) {
                    _events.emit(SummaryEvent.ShowMessage("请先在设置中配置 API Key"))
                    _state.update { it.copy(isGenerating = false) }
                    return@launch
                }
                val (start, end) = TimeUtil.currentMonthRange()
                val entries = repository.getAllEntries().first()
                    .filter { it.type == EntryType.NORMAL && !it.isDeleted && it.createdAt in start..end }
                if (entries.size < 3) {
                    _events.emit(SummaryEvent.ShowMessage("本月记录不足 3 条"))
                    _state.update { it.copy(isGenerating = false) }
                    return@launch
                }
                val result = aiClient.chat(settings.summaryModel, SummaryPrompt.MONTH_LETTER, SummaryPrompt.buildUserPrompt(entries))
                if (result is AiResult.Success && result.value.isNotBlank()) {
                    val existing = _state.value.currentMonthSummary
                    if (existing != null) repository.update(existing.copy(content = result.value, summaryModel = settings.summaryModel))
                    else repository.save(Entry(content = result.value, type = EntryType.SUMMARY, summaryStart = start, summaryEnd = end, summaryModel = settings.summaryModel))
                    _events.emit(SummaryEvent.ShowMessage("月总结已生成"))
                } else {
                    _events.emit(SummaryEvent.ShowMessage("生成失败"))
                }
            } catch (t: Throwable) {
                _events.emit(SummaryEvent.ShowMessage("生成失败: ${t.message ?: ""}"))
            } finally {
                _state.update { it.copy(isGenerating = false) }
            }
        }
    }
}
