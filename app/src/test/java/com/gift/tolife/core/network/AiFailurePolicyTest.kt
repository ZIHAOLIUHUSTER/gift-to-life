package com.gift.tolife.core.network

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class AiFailurePolicyTest {
    @Test
    fun cancellationIsRethrownInsteadOfBecomingRetryable() {
        val cancelled = CancellationException("任务已取消")
        assertSame(cancelled, assertThrows(CancellationException::class.java) {
            retryableAiFailure(cancelled)
        })
    }

    @Test
    fun fatalErrorIsNotRetried() {
        val failure = AssertionError("broken invariant")
        assertSame(failure, assertThrows(AssertionError::class.java) {
            retryableAiFailure(failure)
        })
    }

    @Test
    fun ordinaryFailureKeepsTheOriginalCause() {
        val failure = IOException("connection reset")
        assertSame(failure, retryableAiFailure(failure).cause)
    }

    @Test
    fun imageFallbackDoesNotSwallowCancellation() {
        val cancelled = CancellationException("图片读取已取消")
        assertSame(cancelled, assertThrows(CancellationException::class.java) {
            rethrowUnrecoverableAiFailure(cancelled)
        })
    }
}
