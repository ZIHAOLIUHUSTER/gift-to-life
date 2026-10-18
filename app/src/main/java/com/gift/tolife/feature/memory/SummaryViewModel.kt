package com.gift.tolife.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gift.tolife.core.ai.ManualSummaryScheduler
import com.gift.tolife.core.ai.ManualSummaryStatus
import com.gift.tolife.core.ai.ManualSummaryTask
import com.gift.tolife.core.ai.SummaryGenerationPolicy
import com.gift.tolife.core.ai.SummaryGenerationTarget
import com.gift.tolife.core.ai.SummaryGenerator
import com.gift.tolife.core.ai.SummaryPeriod
import com.gift.tolife.core.ai.SummaryStatsBuilder
import com.gift.tolife.core.common.DateFormats
import com.gift.tolife.core.common.TimeUtil
import com.gift.tolife.core.database.EntryRepository
import com.gift.tolife.core.model.Entry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SummaryState(
    val weekSummaries: List<Entry> = emptyList(),
    val monthSummaries: List<Entry> = emptyList(),
    val currentWeekSummary: Entry? = null,
    val currentMonthSummary: Entry? = null,
    val isGenerating: Boolean = false,
    val generatingPeriod: SummaryPeriod? = null,
    val retryablePeriod: SummaryPeriod? = null,
    val recentWeekCount: Int = 0,
    val recentMonthCount: Int = 0,
    /** 跨离页/进程的任务状态；旧任务只展示状态，不自动导航。 */
    val manualTasks: List<ManualSummaryTask> = emptyList(),
)

sealed class SummaryEvent {
    data class ShowMessage(val message: String) : SummaryEvent()
    data class ConfirmRegenerate(val generatedAt: String) : SummaryEvent()
    data class OpenDeck(val entryId: Long) : SummaryEvent()
    data class OfferRetry(val message: String, val period: SummaryPeriod) : SummaryEvent()
}

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val generator: SummaryGenerator,
    private val scheduler: ManualSummaryScheduler,
) : ViewModel() {
    private val _state = MutableStateFlow(SummaryState())
    val state: StateFlow<SummaryState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<SummaryEvent>()
    val events: SharedFlow<SummaryEvent> = _events.asSharedFlow()

    // 不持久化“自动打开”意图：只有本页面主动加入的请求完成才导航。
    private var requestedId: String? = null
    private var enqueueing = false
    private var pendingRetry: Pair<SummaryGenerationTarget, Boolean>? = null
    private var pendingConfirmation: SummaryGenerationTarget? = null

    init {
        viewModelScope.launch {
            repository.observeSummaries().collect { summaries ->
                val (wStart, wEnd) = TimeUtil.previousWeekRange()
                val (mStart, mEnd) = TimeUtil.previousMonthRange()
                _state.update {
                    it.copy(
                        weekSummaries = summaries.filter { entry -> isWeek(entry) },
                        monthSummaries = summaries.filter { entry -> !isWeek(entry) },
                        currentWeekSummary = summaries.find { s -> s.summaryStart == wStart && s.summaryEnd == wEnd },
                        currentMonthSummary = summaries.find { s -> s.summaryStart == mStart && s.summaryEnd == mEnd },
                    )
                }
            }
        }
        viewModelScope.launch {
            scheduler.observe().collect { tasks ->
                updateTasks(tasks)
                handleRequestedCompletion(tasks)
            }
        }
        refreshCounts()
    }

    private fun isWeek(entry: Entry): Boolean {
        val start = entry.summaryStart ?: return false
        val end = entry.summaryEnd ?: return false
        return end - start < 8L * 24 * 60 * 60 * 1000
    }

    fun refreshCounts() {
        viewModelScope.launch {
            val (wStart, wEnd) = TimeUtil.previousWeekRange()
            val (mStart, mEnd) = TimeUtil.previousMonthRange()
            try {
                val weeks = repository.countInRange(wStart, wEnd)
                val months = repository.countInRange(mStart, mEnd)
                _state.update { it.copy(recentWeekCount = weeks, recentMonthCount = months) }
            } catch (t: Exception) {
                if (t is CancellationException) throw t
            }
        }
    }

    fun generate(period: SummaryPeriod, force: Boolean = false) {
        // 确认弹窗期间跨日也不改变用户刚才选中的范围。
        val confirmed = pendingConfirmation?.takeIf { force && it.period == period }
        pendingConfirmation = null
        generate(confirmed ?: generator.targetOf(period), force)
    }

    fun generate(target: SummaryGenerationTarget, force: Boolean = false) {
        if (enqueueing || requestedId != null) return
        enqueueing = true
        _state.update { it.copy(isGenerating = true, generatingPeriod = target.period, retryablePeriod = null) }
        viewModelScope.launch {
            try {
                if (!target.isValid()) {
                    _events.emit(SummaryEvent.ShowMessage("总结时间范围无效或尚未结束"))
                    return@launch
                }
                val existing = if (target.expectedEntryId != null) repository.getById(target.expectedEntryId)
                    else generator.existingSummary(target.start, target.end)
                if (existing != null && !existing.isDeleted && !force) {
                    pendingConfirmation = target.copy(expectedEntryId = existing.id, expectedUpdatedAt = existing.updatedAt)
                    _events.emit(SummaryEvent.ConfirmRegenerate(DateFormats.formatDateTime(existing.createdAt)))
                    return@launch
                }
                val frozen = if (force && existing != null) target.copy(
                    expectedEntryId = target.expectedEntryId ?: existing.id,
                    expectedUpdatedAt = target.expectedUpdatedAt ?: existing.updatedAt,
                ) else target
                pendingRetry = frozen to force
                requestedId = scheduler.enqueue(frozen, force)
                // 极快的本地跳过/失败可能先于 enqueue 返回，补消费一次最新快照。
                handleRequestedCompletion(_state.value.manualTasks)
            } catch (t: Exception) {
                if (t is CancellationException) throw t
                requestedId = null
                _events.emit(SummaryEvent.OfferRetry("任务未能入队，请再试一次", target.period))
            } finally {
                enqueueing = false
                updateTasks(_state.value.manualTasks)
            }
        }
    }

    private fun updateTasks(tasks: List<ManualSummaryTask>) {
        val active = tasks.firstOrNull { it.running && it.id == requestedId } ?: tasks.firstOrNull { it.running }
        _state.update {
            it.copy(manualTasks = tasks, isGenerating = enqueueing || active != null,
                generatingPeriod = if (enqueueing) it.generatingPeriod else active?.target?.period)
        }
    }

    private suspend fun handleRequestedCompletion(tasks: List<ManualSummaryTask>) {
        val task = tasks.find { it.id == requestedId && !it.running } ?: return
        val shouldOpen = SummaryGenerationPolicy.shouldOpen(task, requestedId)
        requestedId = null // 先消费再 suspend，观察器重复发射不能二次导航。
        when {
            shouldOpen -> {
                pendingRetry = null
                val entry = repository.getById(task.entryId!!)
                if (entry != null && !entry.isDeleted && entry.summaryStart == task.target.start && entry.summaryEnd == task.target.end) {
                    // 不先发成功 Snackbar，避免它阻塞 OpenDeck。
                    _events.emit(SummaryEvent.OpenDeck(entry.id))
                }
            }
            task.status == ManualSummaryStatus.FAILED -> {
                _state.update { it.copy(retryablePeriod = task.target.period) }
                _events.emit(SummaryEvent.OfferRetry(task.message, task.target.period))
            }
            else -> {
                pendingRetry = null
                _events.emit(SummaryEvent.ShowMessage(task.message))
            }
        }
    }

    /** 重试沿用上次冻结范围，不能跨周/月后悄悄换成新周期。 */
    fun retry() {
        val retry = pendingRetry ?: _state.value.manualTasks.lastOrNull {
            it.status == ManualSummaryStatus.FAILED
        }?.let { it.target to (it.target.expectedEntryId != null) } ?: return
        pendingRetry = null
        generate(retry.first, retry.second)
    }

    fun deleteSummary(entryId: Long) {
        viewModelScope.launch { repository.softDelete(entryId) }
    }

    fun bandOf(timestamp: Long): String = SummaryStatsBuilder.bandOf(timestamp)
}
