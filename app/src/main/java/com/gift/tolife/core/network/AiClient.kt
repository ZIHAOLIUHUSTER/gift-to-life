package com.gift.tolife.core.network

import com.gift.tolife.core.ai.AiResult
import com.gift.tolife.core.ai.VisionPrompt
import com.gift.tolife.core.datastore.SettingsDataStore
import com.gift.tolife.core.network.dto.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import retrofit2.HttpException
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

    @Synchronized
    private fun getService(baseUrl: String): OpenAiService? {
        val url = try {
            BaseUrlValidator.normalize(baseUrl)
        } catch (e: Exception) {
            return null
        }
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

    suspend fun chat(model: String, systemPrompt: String, userMessage: String, disableThinking: Boolean = false): AiResult<String> {
        val settings = settingsDataStore.settings.first()
        return chatWith(model, systemPrompt, userMessage, settings.apiKey, settings.baseUrl, disableThinking)
    }

    suspend fun chatWith(model: String, systemPrompt: String, userMessage: String, apiKey: String, baseUrl: String, disableThinking: Boolean = false): AiResult<String> {
        return try {
            if (apiKey.isBlank()) return AiResult.PermanentFailure("API Key not configured")
            val service = getService(baseUrl) ?: return AiResult.PermanentFailure("Invalid base URL")
            val request = ChatRequest(
                model = model,
                messages = listOf(
                    Message(role = "system", content = systemPrompt),
                    Message(role = "user", content = userMessage)
                ),
                enable_thinking = if (disableThinking) false else null
            )
            val response = service.chatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )
            val msg = response.choices?.firstOrNull()?.message
            val result = msg?.content?.takeIf { it.isNotBlank() } ?: msg?.reasoning_content
            if (!result.isNullOrBlank()) AiResult.Success(result)
            else AiResult.PermanentFailure("Empty response")
        } catch (e: java.net.UnknownHostException) {
            AiResult.RetryableFailure(e)
        } catch (e: java.net.SocketTimeoutException) {
            AiResult.RetryableFailure(e)
        } catch (e: java.io.IOException) {
            AiResult.RetryableFailure(e)
        } catch (e: HttpException) {
            when (e.code()) {
                408, 429, in 500..599 -> AiResult.RetryableFailure(e)
                else -> AiResult.PermanentFailure("HTTP ${e.code()}")
            }
        } catch (t: Throwable) {
            AiResult.RetryableFailure(t)
        }
    }

    suspend fun describeImage(model: String, imagePath: String, disableThinking: Boolean = false): AiResult<String> {
        return try {
            val settings = settingsDataStore.settings.first()
            if (settings.apiKey.isBlank()) return AiResult.PermanentFailure("API Key not configured")
            val base64 = encodeImageToBase64(imagePath) ?: return AiResult.PermanentFailure("Image not found")
            val service = getService(settings.baseUrl) ?: return AiResult.PermanentFailure("Invalid base URL")
            val message = VisionMessage(
                role = "user",
                content = listOf(
                    ContentPart(type = "text", text = VisionPrompt.SYSTEM),
                    ContentPart(
                        type = "image_url",
                        image_url = ImageUrl(url = "data:image/webp;base64,$base64")
                    )
                )
            )
            val request = VisionChatRequest(
                model = model,
                messages = listOf(message),
                enable_thinking = if (disableThinking) false else null
            )
            val response = service.chatCompletionVision(
                authorization = "Bearer ${settings.apiKey}",
                request = request
            )
            val msg = response.choices?.firstOrNull()?.message
            val result = msg?.content?.takeIf { it.isNotBlank() } ?: msg?.reasoning_content
            if (!result.isNullOrBlank()) AiResult.Success(result)
            else AiResult.PermanentFailure("Empty response")
        } catch (e: java.net.UnknownHostException) {
            AiResult.RetryableFailure(e)
        } catch (e: java.net.SocketTimeoutException) {
            AiResult.RetryableFailure(e)
        } catch (e: java.io.IOException) {
            AiResult.RetryableFailure(e)
        } catch (e: HttpException) {
            when (e.code()) {
                408, 429, in 500..599 -> AiResult.RetryableFailure(e)
                else -> AiResult.PermanentFailure("HTTP ${e.code()}")
            }
        } catch (t: Throwable) {
            AiResult.RetryableFailure(t)
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
