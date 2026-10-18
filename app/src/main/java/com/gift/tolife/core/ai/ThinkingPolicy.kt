package com.gift.tolife.core.ai

/**
 * 思考（思维链）策略 —— 只用在总结链路上。
 *
 * 背景（2026-09-25 实测 `tencent/Hy4-preview`，同一个 8 条记录的周志输入）：
 *
 * | 设置 | 思考 token | 耗时 |
 * |---|---|---|
 * | 不思考 | 0 | 8s |
 * | `thinking_budget=1024` | 1,022 | 27s |
 * | `thinking={type:enabled}` | 692 | 13s |
 * | `reasoning_effort=high`（无上限） | 7,743 | 127s |
 * | 默认（模型自己定） | 15,900 | 273s |
 *
 * 结论：思考**留着**（用户要求，且高质量总结确实用得上），但必须给上限——
 * 不设上限时思考会吃光输出预算，正文变空（4.4.2~4.4.3 线上就是这么炸的）。
 */
data class ThinkingPolicy(
    val enabled: Boolean,
    /** OpenAI/DeepSeek 风格：low / high / max。 */
    val effort: String?,
    /** SiliconFlow 风格：思维链 token 上限。不支持的供应商会 400，届时会被降级丢掉。 */
    val budgetTokens: Int?,
) {
    companion object {
        const val KEY_OFF = "off"
        const val KEY_HIGH = "high"
        const val KEY_DEEP = "deep"

        /** 不思考：最快最省（实测 8s / 320 token）。 */
        val OFF = ThinkingPolicy(enabled = false, effort = null, budgetTokens = null)

        /** 默认：high 强度 + 思维链上限 2048。 */
        val HIGH_BOUNDED = ThinkingPolicy(enabled = true, effort = "high", budgetTokens = 2048)

        /** 深度：high 强度、不设上限，最慢最贵（分钟级）。 */
        val HIGH_UNBOUNDED = ThinkingPolicy(enabled = true, effort = "high", budgetTokens = null)

        /** 设置项 → 策略；未知值按默认（有上限的 high）。 */
        fun fromSetting(value: String?): ThinkingPolicy = when (value) {
            KEY_OFF -> OFF
            KEY_DEEP -> HIGH_UNBOUNDED
            else -> HIGH_BOUNDED
        }
    }
}

/**
 * 一次总结调用的"计划"：服务端不认某个参数（400）时按 [degraded] 逐级降级重试，
 * 而不是直接失败——不同服务商认的参数完全不一样（response_format / thinking_budget /
 * reasoning_effort / max_tokens 上限）。
 */
data class SummaryCallPlan(
    val jsonMode: Boolean,
    val policy: ThinkingPolicy,
    val maxTokens: Int,
) {
    companion object {
        /** 输出上限：思考 + 正文都要装得下。关思考时实际只用到 300~600。 */
        const val MAX_OUTPUT_TOKENS = 8192

        /** 服务端嫌 8192 太大时回退到的保守值。 */
        const val MIN_OUTPUT_TOKENS = 2048

        /** 降级梯子的最后一格（0 是原始计划）。 */
        const val MAX_LADDER_STEP = 4

        fun initial(policy: ThinkingPolicy) = SummaryCallPlan(
            jsonMode = true,
            policy = policy,
            maxTokens = MAX_OUTPUT_TOKENS,
        )
    }

    /**
     * 降级梯子：
     * 1 丢掉 `thinking_budget`（供应商不认）→ 2 丢掉 `response_format`（不认 JSON 模式）
     * → 3 整个丢思考（连 `reasoning_effort` / `thinking` 都不认）→ 4 缩小 `max_tokens`。
     */
    fun degraded(step: Int): SummaryCallPlan = when (step) {
        1 -> copy(policy = policy.copy(budgetTokens = null))
        2 -> copy(jsonMode = false)
        3 -> copy(policy = ThinkingPolicy.OFF)
        else -> copy(maxTokens = MIN_OUTPUT_TOKENS)
    }
}
