package com.gift.tolife.core.ai

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun enqueue(entryId: Long, revision: Long) {
        val request = OneTimeWorkRequestBuilder<TagWorker>()
            .setInputData(workDataOf("entry_id" to entryId, "entry_revision" to revision))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "tag_entry_$entryId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
