package com.gift.tolife.core.network

import com.gift.tolife.core.ai.AiResult
import kotlinx.coroutines.CancellationException

/** 取消和运行时致命错误不是可以重新发一次请求的网络失败。 */
internal fun rethrowUnrecoverableAiFailure(cause: Throwable) {
    if (cause is CancellationException || cause is Error) throw cause
}

internal fun retryableAiFailure(cause: Throwable): AiResult.RetryableFailure {
    rethrowUnrecoverableAiFailure(cause)
    return AiResult.RetryableFailure(cause)
}
