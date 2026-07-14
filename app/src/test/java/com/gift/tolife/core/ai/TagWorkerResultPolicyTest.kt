package com.gift.tolife.core.ai

import androidx.work.ListenableWorker
import org.junit.Assert.assertEquals
import org.junit.Test

internal fun workerResultFor(result: AiResult<*>): ListenableWorker.Result = when (result) {
    is AiResult.Success -> ListenableWorker.Result.success()
    is AiResult.RetryableFailure -> ListenableWorker.Result.retry()
    is AiResult.PermanentFailure -> ListenableWorker.Result.failure()
}

class TagWorkerResultPolicyTest {
    @Test
    fun successMapsToSuccess() {
        assertEquals(ListenableWorker.Result.success(), workerResultFor(AiResult.Success("ok")))
    }

    @Test
    fun retryableFailureMapsToRetry() {
        assertEquals(ListenableWorker.Result.retry(), workerResultFor(AiResult.RetryableFailure()))
    }

    @Test
    fun permanentFailureMapsToFailure() {
        assertEquals(ListenableWorker.Result.failure(), workerResultFor(AiResult.PermanentFailure("bad")))
    }
}
