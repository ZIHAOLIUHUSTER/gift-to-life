package com.gift.tolife.feature.memory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gift.tolife.core.ai.DeckItem
import com.gift.tolife.core.ai.SummaryCardParser
import com.gift.tolife.core.ai.SummaryDeckBuilder
import com.gift.tolife.core.ai.ManualSummaryScheduler
import com.gift.tolife.core.ai.ManualSummaryStatus
import com.gift.tolife.core.ai.ManualSummaryTask
import com.gift.tolife.core.ai.SummaryGenerationTarget
import com.gift.tolife.core.ai.SummaryPeriod
import com.gift.tolife.core.ai.SummaryStatsBuilder
import com.gift.tolife.core.common.TimeUtil
import com.gift.tolife.core.database.EntryRepository
import com.gift.tolife.core.database.dao.EntryDao
import com.gift.tolife.core.database.dao.EntryTagDao
import com.gift.tolife.core.model.Entry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 牌桌：把一条总结记录渲染成一副可翻的卡。
 *
 * 数据全部在本地拼装：模型只给文案，卡片的数字、背面引用的"现场"都来自真实记录。
 */
@HiltViewModel
class SummaryDeckViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: EntryRepository,
    private val entryDao: EntryDao,
    private val entryTagDao: EntryTagDao,
    private val scheduler: ManualSummaryScheduler,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val ready: Boolean = false,
        val title: String = "",
        val subtitle: String = "",
        val items: List<DeckItem> = emptyList(),
        val isWeek: Boolean = true,
        val regenerating: Boolean = false,
        val message: String? = null,
    )

    private val entryId: Long = savedStateHandle.get<String>("entryId")?.toLongOrNull() ?: -1L

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var current: Entry? = null
    private var requestedId: String? = null
    private var enqueueing = false
    private var tasks: List<ManualSummaryTask> = emptyList()

    init {
        viewModelScope.launch {
            repository.observeSummaries().collectLatest { summaries ->
                val entry = summaries.find { it.id == entryId }
                if (entry == null) {
                    current = null
                    _state.update { it.copy(loading = false, ready = false, message = "这条总结已经不在了") }
                } else if (entry != current) {
                    applyEntry(entry)
                    current = entry
                }
                updateRegenerating()
            }
        }
        viewModelScope.launch {
            scheduler.observe().collect { observed ->
                tasks = observed
                updateRegenerating()
                handleRequestedCompletion()
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            val entry = repository.getById(entryId)
            if (entry == null || entry.isDeleted) {
                _state.update { it.copy(loading = false, ready = false, message = "这条总结已经不在了") }
                return@launch
            }
            current = entry
            applyEntry(entry)
            updateRegenerating()
        }
    }

    private suspend fun applyEntry(entry: Entry) {
        val start = entry.summaryStart ?: 0L
        val end = entry.summaryEnd ?: 0L
        val isWeek = end - start < 8L * 24 * 60 * 60 * 1000
        val entries = entryDao.getNormalEntriesInRange(start, end)
        val tags = if (entries.isEmpty()) emptyMap() else entryTagDao.getByEntryIds(entries.map { it.id })
            .groupBy({ it.entryId }, { it.tag })
        val stats = SummaryStatsBuilder.build(entries, tags)
        val deck = SummaryCardParser.parseOrPlain(entry.content)

        val periodLabel = if (isWeek) {
            "${TimeUtil.year(start)} 年 ${TimeUtil.formatWeek(start, end)}"
        } else {
            TimeUtil.monthLabelForRange(start, end)
        }
        val items = SummaryDeckBuilder.build(
            deck = deck,
            entries = entries,
            stats = stats,
            periodLabel = periodLabel,
            coverLabel = if (isWeek) "本周关键词" else "本月关键词",
            letterLabel = if (isWeek) "这周的信" else "这个月的信",
        )
        _state.update {
            it.copy(
                loading = false,
                ready = true,
                title = if (isWeek) "周志" else "月志",
                subtitle = periodLabel,
                items = items,
                isWeek = isWeek,
            )
        }
    }

    fun regenerate() {
        val entry = current ?: return
        if (_state.value.regenerating || enqueueing || requestedId != null) return
        val start = entry.summaryStart ?: return
        val end = entry.summaryEnd ?: return
        val period = if (_state.value.isWeek) SummaryPeriod.WEEK else SummaryPeriod.MONTH
        val target = SummaryGenerationTarget(period, start, end, entry.id, expectedUpdatedAt = entry.updatedAt)
        enqueueing = true
        _state.update { it.copy(regenerating = true, message = null) }
        viewModelScope.launch {
            try {
                // 明确携带当前历史条目的 id/起止，不能拿 WEEK/MONTH 生成最近周期。
                requestedId = scheduler.enqueue(target, force = true)
                handleRequestedCompletion()
            } catch (t: Exception) {
                if (t is CancellationException) throw t
                _state.update { it.copy(message = "任务未能入队，请再试一次") }
            } finally {
                enqueueing = false
                updateRegenerating()
            }
        }
    }

    private fun updateRegenerating() {
        val entry = current
        val running = tasks.any { task ->
            task.running && (task.id == requestedId || (entry != null &&
                task.target.start == entry.summaryStart && task.target.end == entry.summaryEnd))
        }
        _state.update { it.copy(regenerating = enqueueing || running) }
    }

    private suspend fun handleRequestedCompletion() {
        val task = tasks.find { it.id == requestedId && !it.running } ?: return
        requestedId = null
        if (task.status == ManualSummaryStatus.SUCCESS) {
            // 只重新读当前 id，不能因为 KEEP 加入别的旧任务就切到另一条总结。
            val entry = repository.getById(entryId)
            if (entry != null && !entry.isDeleted) {
                current = entry
                applyEntry(entry)
            }
        } else {
            _state.update { it.copy(message = task.message) }
        }
    }

    fun delete(onDone: () -> Unit) {
        val entry = current ?: return
        viewModelScope.launch {
            repository.softDelete(entry.id)
            onDone()
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
