package com.gift.tolife.core.ai

import java.util.Calendar
import java.util.TimeZone

/** 时间范围在点击时冻结；历史重写绑定原条目，不能退回到“上周/上月”。 */
data class SummaryGenerationTarget(
    val period: SummaryPeriod,
    val start: Long,
    val end: Long,
    val expectedEntryId: Long? = null,
    val zoneId: String = TimeZone.getDefault().id,
    /** 重写绑定点击时版本；落库后进程被杀再执行，也不能再次改写/扣费。 */
    val expectedUpdatedAt: Long? = null,
) {
    val entryId: Long? get() = expectedEntryId

    fun isValid(now: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getTimeZone(zoneId)): Boolean {
        if (start <= 0 || end <= start || end >= now || (expectedEntryId != null && expectedEntryId <= 0) ||
            (expectedUpdatedAt != null && (expectedUpdatedAt < 0 || expectedEntryId == null))) return false
        if (expectedEntryId != null) {
            // 历史条目的自然边界属于创建时所在时区，不能拿旅行后的默认时区否定它。
            // 真正的授权还需 generator 校验 DB 原 id、类型、删除状态和精确起止。
            val duration = end - start + 1
            val day = 24L * 60 * 60 * 1000
            return if (period == SummaryPeriod.WEEK) duration in (6 * day)..(8 * day)
                else duration in (27 * day)..(32 * day)
        }
        if (TimeZone.getTimeZone(zoneId).id == "GMT" && zoneId != "GMT") return false
        val cal = Calendar.getInstance(zone).apply { timeInMillis = start }
        if (cal.get(Calendar.HOUR_OF_DAY) != 0 || cal.get(Calendar.MINUTE) != 0 ||
            cal.get(Calendar.SECOND) != 0 || cal.get(Calendar.MILLISECOND) != 0) return false
        if (period == SummaryPeriod.WEEK) {
            if (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) return false
            cal.add(Calendar.DAY_OF_MONTH, 7)
        } else {
            if (cal.get(Calendar.DAY_OF_MONTH) != 1) return false
            cal.add(Calendar.MONTH, 1)
        }
        return cal.timeInMillis - 1 == end
    }

    // force 与条目 id 不参与命名，同范围的生成和重写不能同时扣费。
    fun uniqueName(): String = "summary_manual_${period.name.lowercase()}_${start}_${end}"
}

enum class ManualSummaryStatus { SUCCESS, SKIPPED, FAILED }

data class ManualSummaryTask(
    val id: String,
    val target: SummaryGenerationTarget,
    val running: Boolean,
    val status: ManualSummaryStatus? = null,
    val entryId: Long? = null,
    val message: String = "",
)

enum class SummaryWorkResult { SUCCESS, RETRY, FAILURE }

data class SummarySourceRevision(val id: Long, val revision: Long)

object SummaryGenerationPolicy {
    fun shouldOpen(task: ManualSummaryTask, requestedId: String?): Boolean =
        requestedId == task.id && !task.running && task.status == ManualSummaryStatus.SUCCESS &&
            (task.entryId ?: 0) > 0

    fun mayOverwrite(target: SummaryGenerationTarget, existingId: Long?, force: Boolean): Boolean =
        force && target.expectedEntryId != null && existingId == target.expectedEntryId

    /** 没有原条目时，二次查重也必须仍为空；有原条目时只能更新同一个 id。 */
    fun originalStillMatches(originalId: Long?, latestId: Long?): Boolean = originalId == latestId

    fun matchesStoredTarget(
        target: SummaryGenerationTarget, id: Long, start: Long?, end: Long?, activeSummary: Boolean,
        updatedAt: Long? = null,
    ): Boolean = activeSummary && target.expectedEntryId == id && target.start == start && target.end == end &&
        (target.expectedUpdatedAt == null || target.expectedUpdatedAt == updatedAt)

    /** 只比较闭合输入范围内的记录，不受今天继续写记录影响；顺序不构成变更。 */
    fun sourceUnchanged(expected: List<SummarySourceRevision>, actual: List<SummarySourceRevision>): Boolean =
        expected.sortedBy { it.id } == actual.sortedBy { it.id }

    fun manualResult(status: ManualSummaryStatus): SummaryWorkResult =
        if (status == ManualSummaryStatus.FAILED) SummaryWorkResult.FAILURE else SummaryWorkResult.SUCCESS

    /** 手动失败是终态，只能显式重试；自动网络失败有上限。 */
    fun automaticResult(retryable: Boolean, attempt: Int): SummaryWorkResult =
        if (retryable && attempt < 2) SummaryWorkResult.RETRY else SummaryWorkResult.SUCCESS

    /** 自动重试保留当前任务；只有终态才排后继，关闭自动开关不排。 */
    fun scheduleSuccessor(autoEnabled: Boolean, retrying: Boolean): Boolean = autoEnabled && !retrying
}
