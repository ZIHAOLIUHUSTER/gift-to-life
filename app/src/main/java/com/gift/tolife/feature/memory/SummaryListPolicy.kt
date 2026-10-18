package com.gift.tolife.feature.memory

import com.gift.tolife.core.ai.SummaryPeriod

/** 当前周期才占用生成入口；别页/历史周期的后台任务不冒充正在写这一份。 */
internal fun summaryGenerationBusy(state: SummaryState, period: SummaryPeriod, range: Pair<Long, Long>): Boolean {
    if (state.manualTasks.any { it.running && it.target.period == period &&
            it.target.start == range.first && it.target.end == range.second }) return true
    // 入队尚未出现在 WorkInfo 时，保留本页瞬时状态。
    return state.manualTasks.none { it.running } && state.isGenerating && state.generatingPeriod == period
}
