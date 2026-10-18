package com.gift.tolife.core.ai

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.gift.tolife.core.common.TimeUtil
import com.gift.tolife.core.datastore.SettingsDataStore
import com.gift.tolife.core.notify.SummaryNotifier
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 到点自动写回顾（2026-09-24 新增）。
 *
 * 用一次性任务 + 跑完再排下一次，而不是 PeriodicWork：
 * 周期任务会随系统调度漂移，而用户对"周一早上/每月 1 号"是有明确预期的。
 */
@Singleton
class SummaryWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        const val KEY_PERIOD = "period"
        const val HOUR_OF_DAY = 9

        fun uniqueName(period: SummaryPeriod) = "summary_auto_${period.name.lowercase()}"

        fun enqueuePolicy(successor: Boolean): ExistingWorkPolicy =
            if (successor) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP

        /**
         * 距离"下一次该写"还有多久：
         * 周志 = 下周一 09:00（今天就是周一且还没到 9 点则是今天），月志 = 下月 1 号 09:00。
         */
        fun delayUntilNext(period: SummaryPeriod, now: Long): Long {
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            if (period == SummaryPeriod.WEEK) {
                cal.set(Calendar.HOUR_OF_DAY, HOUR_OF_DAY)
                cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                val daysToMonday = (Calendar.MONDAY - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
                cal.add(Calendar.DAY_OF_MONTH, daysToMonday)
                if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_MONTH, 7)
            } else {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, HOUR_OF_DAY)
                cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                if (cal.timeInMillis <= now) cal.add(Calendar.MONTH, 1)
            }
            return (cal.timeInMillis - now).coerceAtLeast(0L)
        }
    }

    fun schedule(period: SummaryPeriod, now: Long = System.currentTimeMillis()) =
        enqueue(period, now, successor = false)

    fun scheduleSuccessor(period: SummaryPeriod, now: Long = System.currentTimeMillis()) =
        enqueue(period, now, successor = true)

    private fun enqueue(period: SummaryPeriod, now: Long, successor: Boolean) {
        val delay = delayUntilNext(period, now)
        val anchor = Calendar.getInstance().apply { timeInMillis = now + delay }
        val (start, end) = if (period == SummaryPeriod.WEEK) TimeUtil.previousWeekRangeFor(anchor)
            else TimeUtil.previousMonthRangeFor(anchor)
        val request = OneTimeWorkRequestBuilder<SummaryWorker>()
            .setInputData(workDataOf(KEY_PERIOD to period.name,
                ManualSummaryScheduler.START to start, ManualSummaryScheduler.END to end,
                ManualSummaryScheduler.ZONE to anchor.timeZone.id))
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueName(period), enqueuePolicy(successor), request)
    }

    fun cancel(period: SummaryPeriod) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(period))
    }

    fun scheduleAll() {
        schedule(SummaryPeriod.WEEK)
        schedule(SummaryPeriod.MONTH)
    }

    fun cancelAll() {
        cancel(SummaryPeriod.WEEK)
        cancel(SummaryPeriod.MONTH)
    }
}

/**
 * 后台把上周期回顾写好。失败按指数退避重试，最多两次；没配 Key / 没记录 / 已关闭开关都算正常结束。
 */
class SummaryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun generator(): SummaryGenerator
        fun settings(): SettingsDataStore
        fun notifier(): SummaryNotifier
        fun scheduler(): SummaryWorkScheduler
    }

    override suspend fun doWork(): Result {
        val period = SummaryPeriod.entries.find {
            it.name == inputData.getString(SummaryWorkScheduler.KEY_PERIOD)
        } ?: return Result.success()
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)
        var retrying = false
        val result = try {
            val settings = entryPoint.settings().settings.first()
            if (!settings.autoSummaryEnabled) {
                Result.success()
            } else {
                // 新任务冻结范围；兼容升级前已经持久化、只有 period 的旧任务。
                val target = ManualSummaryScheduler.readTarget(inputData)
                    ?: entryPoint.generator().targetOf(period)
                when (val outcome = entryPoint.generator().generate(target, force = false)) {
                    is SummaryGenerator.Outcome.Success -> {
                        try {
                            entryPoint.notifier().notifyReady(outcome.entry.id, outcome.deck.keyword,
                                period == SummaryPeriod.WEEK)
                        } catch (t: Exception) {
                            if (t is CancellationException) throw t
                            // 落库成功后，通知失败不再扣费。
                        }
                        Result.success()
                    }
                    is SummaryGenerator.Outcome.Skipped -> Result.success()
                    is SummaryGenerator.Outcome.Failed -> {
                        retrying = SummaryGenerationPolicy.automaticResult(outcome.retryable, runAttemptCount) == SummaryWorkResult.RETRY
                        if (retrying) Result.retry() else Result.success()
                    }
                }
            }
        } catch (t: Exception) {
            if (t is CancellationException) throw t
            retrying = SummaryGenerationPolicy.automaticResult(t !is IllegalStateException, runAttemptCount) == SummaryWorkResult.RETRY
            if (retrying) Result.retry() else Result.success()
        }
        // 不能在 finally 中 REPLACE 自己：retry 保留当前任务，终态才接后继。
        val enabled = entryPoint.settings().settings.first().autoSummaryEnabled
        if (SummaryGenerationPolicy.scheduleSuccessor(enabled, retrying)) {
            entryPoint.scheduler().scheduleSuccessor(period)
        }
        return result
    }
}
