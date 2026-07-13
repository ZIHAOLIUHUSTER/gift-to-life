package com.gift.tolife.core.ai

import android.content.Context
import androidx.work.*
import com.gift.tolife.core.database.EntryRepository
import com.gift.tolife.core.datastore.SettingsDataStore
import com.gift.tolife.core.model.TagType
import com.gift.tolife.core.network.AiClient
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

class TagWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun repository(): EntryRepository
        fun aiClient(): AiClient
        fun settingsDataStore(): SettingsDataStore
    }

    override suspend fun doWork(): Result {
        val entryId = inputData.getLong("entry_id", -1)
        if (entryId == -1L) return Result.failure()

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WorkerEntryPoint::class.java
        )
        val repository = entryPoint.repository()
        val aiClient = entryPoint.aiClient()
        val settingsDataStore = entryPoint.settingsDataStore()

        val entry = repository.getById(entryId) ?: return Result.failure()
        val settings = settingsDataStore.settings.first()

        if (settings.apiKey.isBlank()) return Result.failure()

        try {
            var content = entry.content
            if (!entry.imagePath.isNullOrBlank()) {
                val description = aiClient.describeImage(
                    settings.visionModel, entry.imagePath,
                    disableThinking = true
                )
                if (description != null) {
                    repository.update(entry.copy(imageDescription = description))
                    content = if (content.isNotBlank()) "$content\n[图片描述: $description]" else description
                }
            }

            if (content.isNotBlank()) {
                val response = aiClient.chat(
                    model = settings.tagModel,
                    systemPrompt = TagPrompt.SYSTEM,
                    userMessage = TagPrompt.userPrompt(content),
                    disableThinking = true
                )
                if (response != null) {
                    val tags = parseTags(response)
                    if (tags.isNotEmpty()) {
                        repository.setTags(entryId, tags)
                    }
                }
            }

            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }

    private fun parseTags(response: String): List<TagType> {
        return response.split(",", "，").mapNotNull { tag ->
            when (tag.trim()) {
                "闪念" -> TagType.FLASH_THOUGHT
                "事记" -> TagType.EVENT
                "情绪" -> TagType.EMOTION
                "知识" -> TagType.KNOWLEDGE
                else -> null
            }
        }.distinct()
    }

    companion object {
        fun enqueue(context: Context, entryId: Long) {
            val work = OneTimeWorkRequestBuilder<TagWorker>()
                .setInputData(Data.Builder().putLong("entry_id", entryId).build())
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag("tag_$entryId")
                .build()
            WorkManager.getInstance(context).enqueue(work)
        }
    }
}
