package com.gift.tolife.core.ai

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import com.gift.tolife.core.notify.SummaryNotifier
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 不读取自动开关，不碰自动任务；输入仅冻结范围/id/force，密钥运行时读取。 */
@Singleton
class ManualSummaryScheduler @Inject constructor(@ApplicationContext context: Context) {
    private val workManager = WorkManager.getInstance(context)

    fun observe(): Flow<List<ManualSummaryTask>> = workManager.getWorkInfosByTagFlow(TAG).map { infos ->
        infos.mapNotNull { info ->
            val target = targetFromTags(info.tags) ?: return@mapNotNull null
            ManualSummaryTask(
                id = info.id.toString(), target = target, running = !info.state.isFinished,
                status = if (!info.state.isFinished) null else when (info.outputData.getString(STATUS)) {
                    ManualSummaryStatus.SUCCESS.name -> ManualSummaryStatus.SUCCESS
                    ManualSummaryStatus.SKIPPED.name -> ManualSummaryStatus.SKIPPED
                    else -> ManualSummaryStatus.FAILED
                },
                entryId = info.outputData.getLong(ENTRY_ID, -1).takeIf { it > 0 },
                message = info.outputData.getString(ERROR) ?: if (info.state == WorkInfo.State.CANCELLED) "任务已取消" else "生成失败，请再试一次",
            )
        }
    }

    suspend fun enqueue(target: SummaryGenerationTarget, force: Boolean): String {
        require(target.isValid()) { "总结时间范围无效或尚未结束" }
        val request = OneTimeWorkRequestBuilder<ManualSummaryWorker>()
            .setInputData(workDataOf(
                PERIOD to target.period.name, START to target.start, END to target.end,
                EXPECTED_ID to (target.expectedEntryId ?: -1L), FORCE to force, ZONE to target.zoneId,
                EXPECTED_UPDATED_AT to (target.expectedUpdatedAt ?: -1L),
            ))
            .addTag(TAG)
            .addTag("zone:${target.zoneId}")
            .addTag("updated:${target.expectedUpdatedAt ?: -1L}")
            .addTag("target:${target.period.name}:${target.start}:${target.end}:${target.expectedEntryId ?: -1L}")
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(target.uniqueName(), enqueuePolicy(), request).await()
        val infos = workManager.getWorkInfosForUniqueWorkFlow(target.uniqueName()).first()
        // KEEP 可能加入已有请求；主动点击加入该任务也可以收到本页完成事件。
        return (infos.find { it.id == request.id } ?: infos.find { !it.state.isFinished }
            // KEEP 加入的任务可能在查询前已结束；同名手动工作没有 APPEND 链。
            ?: infos.singleOrNull() ?: error("任务未能入队，请再试一次")).id.toString()
    }

    companion object {
        const val TAG = "summary_manual"
        const val PERIOD = "period"
        const val START = "start"
        const val END = "end"
        const val EXPECTED_ID = "expected_entry_id"
        const val FORCE = "force"
        const val ZONE = "zone"
        const val EXPECTED_UPDATED_AT = "expected_updated_at"
        const val STATUS = "status"
        const val ENTRY_ID = "entry_id"
        const val ERROR = "error"

        fun enqueuePolicy(): ExistingWorkPolicy = ExistingWorkPolicy.KEEP

        private fun targetFromTags(tags: Set<String>): SummaryGenerationTarget? {
            val parts = tags.firstOrNull { it.startsWith("target:") }?.split(':') ?: return null
            if (parts.size != 5) return null
            val period = SummaryPeriod.entries.find { it.name == parts[1] } ?: return null
            return SummaryGenerationTarget(period, parts[2].toLongOrNull() ?: return null,
                parts[3].toLongOrNull() ?: return null, parts[4].toLongOrNull()?.takeIf { it > 0 },
                tags.firstOrNull { it.startsWith("zone:") }?.removePrefix("zone:") ?: java.util.TimeZone.getDefault().id,
                tags.firstOrNull { it.startsWith("updated:") }?.removePrefix("updated:")?.toLongOrNull()?.takeIf { it >= 0 })
        }

        fun readTarget(data: Data): SummaryGenerationTarget? {
            val period = SummaryPeriod.entries.find { it.name == data.getString(PERIOD) } ?: return null
            if (!data.keyValueMap.containsKey(START) || !data.keyValueMap.containsKey(END)) return null
            return SummaryGenerationTarget(period, data.getLong(START, -1), data.getLong(END, -1),
                data.getLong(EXPECTED_ID, -1).takeIf { it != -1L },
                data.getString(ZONE) ?: java.util.TimeZone.getDefault().id,
                data.getLong(EXPECTED_UPDATED_AT, -1).takeIf { it != -1L })
        }

        fun output(status: ManualSummaryStatus, entryId: Long? = null, error: String = ""): Data =
            workDataOf(STATUS to status.name, ENTRY_ID to (entryId ?: -1L), ERROR to error.take(180))
    }
}

class ManualSummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun generator(): SummaryGenerator
        fun notifier(): SummaryNotifier
    }

    override suspend fun doWork(): Result {
        val target = ManualSummaryScheduler.readTarget(inputData)
        if (target == null || !target.isValid()) return failed("总结时间范围无效或尚未结束")
        val dependencies = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        return try {
            when (val outcome = dependencies.generator().generate(target, inputData.getBoolean(ManualSummaryScheduler.FORCE, false))) {
                is SummaryGenerator.Outcome.Success -> {
                    // 通知失败不能把已落库结果改判失败或触发再次扣费。
                    try {
                        dependencies.notifier().notifyReady(outcome.entry.id, outcome.deck.keyword, target.period == SummaryPeriod.WEEK)
                    } catch (t: Exception) {
                        if (t is CancellationException) throw t
                    }
                    terminal(ManualSummaryStatus.SUCCESS, outcome.entry.id)
                }
                is SummaryGenerator.Outcome.Skipped -> terminal(ManualSummaryStatus.SKIPPED, error = outcome.message)
                is SummaryGenerator.Outcome.Failed -> failed(when (outcome.message) {
                    "原总结已删除或替换，请重新打开", "总结时间范围无效或尚未结束",
                    "记录已变化，请重新整理" -> outcome.message
                    else -> "生成未完成，请检查网络或模型设置后重试"
                })
            }
        } catch (t: Exception) {
            if (t is CancellationException) throw t
            // 不输出服务商原文/异常详情，避免把鉴权信息持久化到 WorkManager。
            failed(if (t is IllegalStateException) "原总结已删除或替换，请重新打开" else "生成失败，请再试一次")
        }
    }

    private fun failed(message: String): Result = terminal(ManualSummaryStatus.FAILED, error = message)

    private fun terminal(status: ManualSummaryStatus, entryId: Long? = null, error: String = ""): Result {
        val output = ManualSummaryScheduler.output(status, entryId, error)
        return if (SummaryGenerationPolicy.manualResult(status) == SummaryWorkResult.FAILURE) Result.failure(output)
            else Result.success(output)
    }
}
