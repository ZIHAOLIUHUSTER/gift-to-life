package com.gift.tolife.core.ai

import androidx.room.withTransaction
import com.gift.tolife.core.database.AppDatabase
import com.gift.tolife.core.common.TimeUtil
import com.gift.tolife.core.database.EntryRepository
import com.gift.tolife.core.database.dao.EntryDao
import com.gift.tolife.core.database.dao.EntryTagDao
import com.gift.tolife.core.datastore.SettingsDataStore
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.EntryType
import com.gift.tolife.core.model.TagType
import com.gift.tolife.core.network.AiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 周/月总结的生成入口。
 *
 * 手动持久任务与自动任务共用串行入口；范围在请求入队时冻结。
 */
@Singleton
class SummaryGenerator @Inject constructor(
    private val entryDao: EntryDao,
    private val entryTagDao: EntryTagDao,
    private val repository: EntryRepository,
    private val settingsDataStore: SettingsDataStore,
    private val aiClient: AiClient,
    private val database: AppDatabase,
) {

    sealed interface Outcome {
        data class Success(val entry: Entry, val deck: SummaryDeck, val regenerated: Boolean) : Outcome

        /** 正常跳过（没配 Key、记录不够、周期还没结束……），不需要提示失败。 */
        data class Skipped(val message: String) : Outcome

        data class Failed(val message: String, val retryable: Boolean) : Outcome
    }

    /** 月志额外带上过去两个月的记录作为背景（不足则标注"没有记录"）。 */
    private val backfillMonths = 2

    // 自动与手动共用单例锁，覆盖查重、网络调用和落库全过程。
    private val generationMutex = Mutex()

    fun rangeOf(period: SummaryPeriod): Pair<Long, Long> =
        if (period == SummaryPeriod.WEEK) TimeUtil.previousWeekRange() else TimeUtil.previousMonthRange()

    suspend fun existingSummary(start: Long, end: Long): Entry? =
        entryDao.getSummaryForRange(start, end)

    fun targetOf(period: SummaryPeriod): SummaryGenerationTarget {
        val anchor = Calendar.getInstance()
        val (start, end) = if (period == SummaryPeriod.WEEK) TimeUtil.previousWeekRangeFor(anchor)
            else TimeUtil.previousMonthRangeFor(anchor)
        return SummaryGenerationTarget(period, start, end, zoneId = anchor.timeZone.id)
    }

    suspend fun generate(period: SummaryPeriod, force: Boolean = false): Outcome {
        val target = targetOf(period)
        // 兼容旧调用，但重写也必须先绑定原条目，不能覆盖排队后出现的新总结。
        val original = if (force) existingSummary(target.start, target.end) else null
        return generate(target.copy(expectedEntryId = original?.id, expectedUpdatedAt = original?.updatedAt), force)
    }

    suspend fun generate(target: SummaryGenerationTarget, force: Boolean = false): Outcome =
        generationMutex.withLock {
            try {
                generateLocked(target, force)
            } catch (t: Exception) {
                if (t is CancellationException) throw t
                if (t is InputsChangedException) Outcome.Failed("记录已变化，请重新整理", retryable = false)
                else throw t
            }
        }

    private class InputsChangedException : IllegalStateException("记录已变化，请重新整理")
    private data class SourceSnapshot(val start: Long, val end: Long, val revisions: List<SummarySourceRevision>)
    private fun List<Entry>.revisions() = map { SummarySourceRevision(it.id, it.entryRevision) }

    private suspend fun generateLocked(target: SummaryGenerationTarget, force: Boolean): Outcome {
        if (!target.isValid()) return Outcome.Failed("总结时间范围无效或尚未结束", retryable = false)
        val period = target.period
        val start = target.start
        val end = target.end
        val existing = currentSummary(target)
        if (target.expectedEntryId != null && existing?.id != target.expectedEntryId) {
            return Outcome.Failed("原总结已删除或替换，请重新打开", retryable = false)
        }
        val settings = settingsDataStore.settings.first()
        if (settings.effectiveSummaryApiKey.isBlank()) return Outcome.Skipped("请先在设置里配置 API Key")
        if (existing != null && !SummaryGenerationPolicy.mayOverwrite(target, existing.id, force)) {
            return Outcome.Skipped("该周期已经写过了")
        }

        val entries = entryDao.getNormalEntriesInRange(start, end)
        if (entries.size < SummaryPrompt.MIN_ENTRIES) {
            return Outcome.Skipped("这段时间没有记录")
        }

        val tags = loadTags(entries)
        val stats = SummaryStatsBuilder.build(entries, tags)
        val background = if (period == SummaryPeriod.MONTH) backfillRanges(target).map { (from, to) ->
            val records = entryDao.getNormalEntriesInRange(from, to)
            SourceSnapshot(from, to, records.revisions()) to SummaryPrompt.BackfillMonth(
                label = TimeUtil.monthLabelForRange(from, to), entries = records,
            )
        } else emptyList()
        val sources = listOf(SourceSnapshot(start, end, entries.revisions())) + background.map { it.first }
        val backfill = background.map { it.second }
        val prompt = SummaryPrompt.buildUserPrompt(period, entries, stats, tags, backfill)
            ?: return Outcome.Skipped("这段时间没有足够的记录")

        val system = SummaryPrompt.systemFor(period)

        // 最多两次网络尝试；另有一把"参数降级梯子"（服务商不认某参数时逐级丢，不占重试次数）
        var attempts = 0
        var ladderStep = 0
        var plan = SummaryCallPlan.initial(ThinkingPolicy.fromSetting(settings.summaryThinking))
        var salvageable: String? = null
        var lastMessage = "生成失败"
        var lastRetryable = true

        while (attempts < 2) {
            attempts++
            when (val result = aiClient.chatWith(
                model = settings.summaryModel,
                systemPrompt = system,
                userMessage = prompt,
                apiKey = settings.effectiveSummaryApiKey,
                baseUrl = settings.effectiveSummaryBaseUrl,
                jsonMode = plan.jsonMode,
                maxTokens = plan.maxTokens,
                temperature = 0.6,
                thinkingPolicy = plan.policy,
            )) {
                is AiResult.Success -> {
                    val parsed = SummaryCardParser.parseDetailed(result.value)
                    if (parsed != null) {
                        val saved = persist(existing, parsed.json, target, settings.summaryModel, sources)
                        return Outcome.Success(saved, parsed.deck, regenerated = existing != null)
                    }
                    // 有内容但形状不对：先留着兑底，再试一次
                    salvageable = result.value
                    lastMessage = "模型没有按格式返回"
                }
                is AiResult.ReasoningOnly -> {
                    // 思考吃光了输出额度、正文为空。模型常在思考里写好几种草稿，
                    // 最后一版就是定稿 —— 捞得到就用，捞不到才明说原因。
                    // 注意：绝不把思考过程本身当正文摆给用户看（4.4.2 的线上事故）。
                    val parsed = SummaryCardParser.parseDetailed(result.reasoning)
                    if (parsed != null) {
                        val saved = persist(existing, parsed.json, target, settings.summaryModel, sources)
                        return Outcome.Success(saved, parsed.deck, regenerated = existing != null)
                    }
                    lastMessage = "模型只顾着想（约 ${result.reasoning.length} 字），没给出正文"
                }
                is AiResult.RetryableFailure -> {
                    lastMessage = friendlyError(result.cause)
                }
                is AiResult.PermanentFailure -> {
                    // 服务商不认某个参数就逐级降级再试一次，不占重试次数（见 SummaryCallPlan.degraded）
                    if (result.code == 400 && ladderStep < SummaryCallPlan.MAX_LADDER_STEP) {
                        ladderStep++
                        plan = plan.degraded(ladderStep)
                        attempts--
                        lastMessage = result.message
                        continue
                    }
                    // 带上模型名：出错时用户能立刻看出用的是不是自己设定的总结模型
                    lastMessage = "${result.message}（总结模型：${settings.summaryModel}）"
                    lastRetryable = false
                }
            }
            if (!lastRetryable) break
        }

        // 两次都没成形：只要拿到过文字，就按纯文本展示，不让用户白等一趟
        salvageable?.let { raw ->
            val deck = SummaryCardParser.asPlainDeck(raw)
            if (deck.cards.isNotEmpty()) {
                val saved = persist(existing, raw, target, settings.summaryModel, sources)
                return Outcome.Success(saved, deck, regenerated = existing != null)
            }
        }
        return Outcome.Failed(lastMessage, lastRetryable)
    }

    /** 卡片 JSON 直接存进 content（TEXT），不新增列、不动数据库版本。 */
    private suspend fun persist(
        existing: Entry?,
        rawResponse: String,
        target: SummaryGenerationTarget,
        model: String,
        sources: List<SourceSnapshot>,
    ): Entry = database.withTransaction {
        // 输入和查重在同一事务里二次校验；旧网络结果不能写进导入替换后的库。
        for (source in sources) {
            val current = entryDao.getNormalEntriesInRange(source.start, source.end).revisions()
            if (!SummaryGenerationPolicy.sourceUnchanged(source.revisions, current)) throw InputsChangedException()
        }
        val start = target.start
        val end = target.end
        // 查重和写入处于同一事务，删除/导入不能插进校验与更新之间。
        val latest = currentSummary(target)
        check(SummaryGenerationPolicy.originalStillMatches(existing?.id, latest?.id)) {
            "原总结已删除或替换，请重新打开"
        }
        if (target.expectedEntryId != null) {
            check(latest?.id == target.expectedEntryId) { "原总结已删除或替换，请重新打开" }
        }
        val payload = SummaryCardParser.extractJsonObject(rawResponse) ?: rawResponse
        if (latest != null) {
            val updated = latest.copy(
                content = payload, summaryModel = model,
                updatedAt = maxOf(System.currentTimeMillis(), latest.updatedAt + 1),
            )
            // 修订时间至少前进 1ms，避免快速完成/时钟回拨时恢复的 force 任务再次扣费。
            entryDao.update(updated)
            updated
        } else {
            val entry = Entry(
                content = payload,
                type = EntryType.SUMMARY,
                summaryStart = start,
                summaryEnd = end,
                summaryModel = model,
            )
            val id = repository.save(entry)
            entry.copy(id = id)
        }
    }

    private suspend fun currentSummary(target: SummaryGenerationTarget): Entry? {
        val id = target.expectedEntryId ?: return entryDao.getSummaryForRange(target.start, target.end)
        return entryDao.getById(id)?.takeIf {
            SummaryGenerationPolicy.matchesStoredTarget(target, it.id, it.summaryStart, it.summaryEnd,
                !it.isDeleted && it.type == EntryType.SUMMARY, it.updatedAt)
        }
    }

    private suspend fun loadTags(entries: List<Entry>): Map<Long, List<TagType>> {
        if (entries.isEmpty()) return emptyMap()
        val ids = entries.map { it.id }
        return entryTagDao.getByEntryIds(ids)
            .groupBy({ it.entryId }, { it.tag })
    }

    private fun backfillRanges(target: SummaryGenerationTarget): List<Pair<Long, Long>> =
        summaryBackgroundRanges(target, backfillMonths)

    private fun friendlyError(cause: Throwable?): String = when (cause) {
        is SocketTimeoutException -> "等了太久还没等到结果"
        is UnknownHostException -> "网络连不上"
        null -> "网络出了点问题"
        else -> "网络出了点问题（${cause.javaClass.simpleName}）"
    }
}
