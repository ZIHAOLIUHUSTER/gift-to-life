package com.gift.tolife.core.ai

sealed interface AiResult<out T> {
    data class Success<T>(val value: T) : AiResult<T>
    data class RetryableFailure(val cause: Throwable? = null) : AiResult<Nothing>

    /**
     * 不可重试的失败。
     *
     * @param code HTTP 状态码（网络层失败时才有）；调用方可据此做针对性兜底，
     *             例如服务端不认 response_format 时改用普通模式再试一次。
     */
    data class PermanentFailure(val message: String, val code: Int? = null) : AiResult<Nothing>

    /**
     * 服务端只回了思考过程、没有正文（思考吃满配额或被截断）。
     *
     * 注意：**思考不是答案**，调用方不得直接把它当结果展示（4.4.2 的线上事故：
     * 卡片里出现模型自言自语 "Card 3 (insight): … Let me count …"）。
     * 正确用法是尝试从里面捞出结构化 JSON（模型常在思考末尾写定稿）。
     */
    data class ReasoningOnly(val reasoning: String, val finishReason: String? = null) : AiResult<Nothing>
}
