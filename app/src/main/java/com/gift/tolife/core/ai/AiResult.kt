package com.gift.tolife.core.ai

sealed interface AiResult<out T> {
    data class Success<T>(val value: T) : AiResult<T>
    data class RetryableFailure(val cause: Throwable? = null) : AiResult<Nothing>
    data class PermanentFailure(val message: String) : AiResult<Nothing>
}
