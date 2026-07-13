package com.gift.tolife.core.network

import com.gift.tolife.core.ai.VisionPrompt
import com.gift.tolife.core.datastore.SettingsDataStore
import com.gift.tolife.core.network.dto.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiClient @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var cachedBaseUrl: String = ""
    private var cachedService: OpenAiService? = null

    private fun getService(baseUrl: String): OpenAiService? {
        val url = normalizeUrl(baseUrl) ?: return null
        if (url != cachedBaseUrl || cachedService == null) {
            cachedService = try {
                Retrofit.Builder()
                    .baseUrl(url)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(OpenAiService::class.java)
            } catch (e: Exception) {
                null
            }
            cachedBaseUrl = url
        }
        return cachedService
    }

    private fun normalizeUrl(raw: String): String? {
        var url = raw.trim()
        if (url.isEmpty()) return null
        if (url.endsWith("/v1/chat/completions")) {
            url = url.removeSuffix("/v1/chat/completions")
        }
        if (!url.endsWith("/")) url += "/"
        return url
    }

    suspend fun chat(model: String, systemPrompt: String, userMessage: String): String? {
        return try {
            val settings = settingsDataStore.settings.first()
            if (settings.apiKey.isBlank()) return null
            val service = getService(settings.baseUrl) ?: return null
            val request = ChatRequest(
                model = model,
                messages = listOf(
                    Message(role = "system", content = systemPrompt),
                    Message(role = "user", content = userMessage)
                )
            )
            val response = service.chatCompletion(
                authorization = "Bearer ${settings.apiKey}",
                request = request
            )
            val msg = response.choices?.firstOrNull()?.message
            msg?.content?.takeIf { it.isNotBlank() } ?: msg?.reasoning_content
        } catch (t: Throwable) {
            null
        }
    }

    suspend fun describeImage(model: String, imagePath: String): String? {
        return try {
            val settings = settingsDataStore.settings.first()
            if (settings.apiKey.isBlank()) return null
            val base64 = encodeImageToBase64(imagePath) ?: return null
            val service = getService(settings.baseUrl) ?: return null
            val message = VisionMessage(
                role = "user",
                content = listOf(
                    ContentPart(type = "text", text = VisionPrompt.SYSTEM),
                    ContentPart(
                        type = "image_url",
                        image_url = ImageUrl(url = "data:image/jpeg;base64,$base64")
                    )
                )
            )
            val request = VisionChatRequest(
                model = model,
                messages = listOf(message)
            )
            val response = service.chatCompletionVision(
                authorization = "Bearer ${settings.apiKey}",
                request = request
            )
            val msg = response.choices?.firstOrNull()?.message
            msg?.content?.takeIf { it.isNotBlank() } ?: msg?.reasoning_content
        } catch (t: Throwable) {
            null
        }
    }

    private fun encodeImageToBase64(path: String): String? {
        return try {
            val file = java.io.File(path)
            if (!file.exists()) return null
            val bytes = file.readBytes()
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (t: Throwable) {
            null
        }
    }
}
