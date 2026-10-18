package com.gift.tolife.core.ai

import android.content.Context
import androidx.work.*
import com.gift.tolife.core.database.EntryRepository
import com.gift.tolife.core.database.EntryTransactions
import com.gift.tolife.core.datastore.SettingsDataStore
import com.gift.tolife.core.model.TagType
import com.gift.tolife.core.network.AiClient
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

class TagWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun repository(): EntryRepository
        fun transactions(): EntryTransactions
        fun aiClient(): AiClient
        fun settingsDataStore(): SettingsDataStore
    }

    override suspend fun doWork(): Result {
        val entryId = inputData.getLong("entry_id", -1)
        val expectedRevision = inputData.getLong("entry_revision", -1)
        if (entryId == -1L || expectedRevision == -1L) return Result.failure()

        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)
        val repository = entryPoint.repository()
        val transactions = entryPoint.transactions()
        val entry = repository.getById(entryId) ?: return Result.success()
        if (entry.isDeleted || entry.entryRevision != expectedRevision) return Result.success()
        // 编辑或遗留任务也必须遵守手动模式，在任何网络请求前检查。
        if (!TagType.allowsAiEnhancement(repository.getTags(entryId).map { it.tag })) return Result.success()

        val settings = entryPoint.settingsDataStore().settings.first()
        if (settings.apiKey.isBlank()) return Result.success()
        val aiClient = entryPoint.aiClient()

        var content = entry.content
        var imageDescription: String? = null

        if (!entry.imagePath.isNullOrBlank()) {
            when (val descResult = aiClient.describeImage(settings.visionModel, entry.imagePath, disableThinking = true)) {
                is AiResult.Success -> {
                    imageDescription = descResult.value
                    content = if (content.isNotBlank()) "$content\n[图片描述: ${descResult.value}]" else descResult.value
                }
                is AiResult.RetryableFailure -> return Result.retry()
                is AiResult.ReasoningOnly -> {
                    if (content.isBlank()) return Result.failure()
                    imageDescription = entry.imageDescription
                }
                is AiResult.PermanentFailure -> {
                    if (content.isBlank()) return Result.failure()
                    imageDescription = entry.imageDescription
                }
            }
        }

        if (content.isNotBlank()) {
            when (val tagResult = aiClient.chat(
                model = settings.tagModel,
                systemPrompt = TagPrompt.SYSTEM,
                userMessage = TagPrompt.userPrompt(content),
                disableThinking = true
            )) {
                is AiResult.Success -> {
                    val tags = parseTags(tagResult.value).toSet()
                    if (tags.isNotEmpty()) {
                        transactions.applyAiEnhancement(entryId, expectedRevision, imageDescription, tags)
                    }
                    return Result.success()
                }
                is AiResult.RetryableFailure -> return Result.retry()
                is AiResult.ReasoningOnly -> return Result.failure()
                is AiResult.PermanentFailure -> return Result.failure()
            }
        }

        return Result.success()
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
}
