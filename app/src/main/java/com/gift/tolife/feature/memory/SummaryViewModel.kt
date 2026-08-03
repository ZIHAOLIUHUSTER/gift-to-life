package com.gift.tolife.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gift.tolife.core.ai.SummaryPrompt
import com.gift.tolife.core.common.TimeUtil
import com.gift.tolife.core.database.EntryRepository
import com.gift.tolife.core.database.dao.EntryDao
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
    val isGenerating: Boolean = false
)

sealed class SummaryEvent {
    data class ShowMessage(val message: String) : SummaryEvent()
}

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val entryDao: EntryDao,
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
                val (wStart, wEnd) = TimeUtil.previousWeekRange()
                val (mStart, mEnd) = TimeUtil.previousMonthRange()
                _state.update {
                    it.copy(
                        weekSummaries = weeks,
                        monthSummaries = months,
                        currentWeekSummary = summaries.find { s -> s.summaryStart == wStart && s.summaryEnd == wEnd },
                        currentMonthSummary = summaries.find { s -> s.summaryStart == mStart && s.summaryEnd == mEnd }
                    )
                }
            }
        }
    }

    fun generateWeekSummary() {
        val (start, end) = TimeUtil.previousWeekRange()
        generateSummary(
            start = start, end = end,
            systemPrompt = SummaryPrompt.pickRandomWeekPrompt(),
            existing = _state.value.currentWeekSummary,
            insufficientMsg = "上周记录不足 3 条，无法生成总结",
            successMsg = "上周总结已生成"
        )
    }

    fun generateMonthSummary() {
        val (start, end) = TimeUtil.previousMonthRange()
        generateSummary(
            start = start, end = end,
            systemPrompt = SummaryPrompt.MONTH_LETTER,
            existing = _state.value.currentMonthSummary,
            insufficientMsg = "上月记录不足 3 条，无法生成总结",
            successMsg = "上月总结已生成"
        )
    }

    private fun generateSummary(
        start: Long, end: Long,
        systemPrompt: String,
        existing: Entry?,
        insufficientMsg: String,
        successMsg: String
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true) }
            try {
                val settings = settingsDataStore.settings.first()
                if (settings.apiKey.isBlank()) {
                    _events.emit(SummaryEvent.ShowMessage("请先在设置中配置 API Key"))
                    return@launch
                }
                val entries = entryDao.getNormalEntriesInRange(start, end)
                val prompt = SummaryPrompt.buildUserPrompt(entries)
                if (prompt == null) {
                    _events.emit(SummaryEvent.ShowMessage(insufficientMsg))
                    return@launch
                }
                val result = aiClient.chat(settings.summaryModel, systemPrompt, prompt)
                if (result is AiResult.Success && result.value.isNotBlank()) {
                    if (existing != null) repository.update(existing.copy(content = result.value, summaryModel = settings.summaryModel))
                    else repository.save(Entry(content = result.value, type = EntryType.SUMMARY, summaryStart = start, summaryEnd = end, summaryModel = settings.summaryModel))
                    _events.emit(SummaryEvent.ShowMessage(successMsg))
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

    fun deleteSummary(entryId: Long) {
        viewModelScope.launch {
            repository.softDelete(entryId)
        }
    }
}
