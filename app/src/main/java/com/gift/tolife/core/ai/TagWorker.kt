package com.gift.tolife.core.ai

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.gift.tolife.core.database.EntryRepository
import com.gift.tolife.core.datastore.SettingsDataStore
import com.gift.tolife.core.model.TagType
import com.gift.tolife.core.network.AiClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class TagWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: EntryRepository,
    private val aiClient: AiClient,
    private val settingsDataStore: SettingsDataStore
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val entryId = inputData.getLong("entry_id", -1)
        if (entryId == -1L) return Result.failure()

        val entry = repository.getById(entryId) ?: return Result.failure()
        val settings = settingsDataStore.settings.first()

        if (settings.apiKey.isBlank()) return Result.failure()

        try {
            // Step 1: 如果只有图片没有文字，先做视觉识别
            var content = entry.content
            if (content.isBlank() && !entry.imagePath.isNullOrBlank()) {
                val description = aiClient.describeImage(settings.visionModel, entry.imagePath)
                if (description != null) {
                    repository.update(entry.copy(imageDescription = description))
                    content = description
                }
            }

            // Step 2: 如果有内容（原文或视觉描述），打标签
            if (content.isNotBlank()) {
                val response = aiClient.chat(
                    model = settings.tagModel,
                    systemPrompt = TagPrompt.SYSTEM,
                    userMessage = TagPrompt.userPrompt(content)
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
        fun enqueue(entryId: Long) {
            val work = OneTimeWorkRequestBuilder<TagWorker>()
                .setInputData(Data.Builder().putLong("entry_id", entryId).build())
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag("tag_$entryId")
                .build()
            WorkManager.getInstance().enqueue(work)
        }
    }
}
