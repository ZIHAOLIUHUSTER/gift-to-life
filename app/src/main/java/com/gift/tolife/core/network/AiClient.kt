package com.gift.tolife.core.network

import com.gift.tolife.core.ai.VisionPrompt
import com.gift.tolife.core.datastore.SettingsDataStore
import com.gift.tolife.core.network.dto.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiClient @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    suspend fun chat(model: String, systemPrompt: String, userMessage: String): String? {
        return try {
            val settings = settingsDataStore.settings.first()
            if (settings.apiKey.isBlank()) return null
            val service = createService(settings.baseUrl) ?: return null
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
            response.choices?.firstOrNull()?.message?.content
        } catch (e: Exception) {
            null
        }
    }

    suspend fun describeImage(model: String, imagePath: String): String? {
        return try {
            val settings = settingsDataStore.settings.first()
            if (settings.apiKey.isBlank()) return null

            val base64 = encodeImageToBase64(imagePath) ?: return null
            val service = createService(settings.baseUrl) ?: return null
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
            response.choices?.firstOrNull()?.message?.content
        } catch (e: Exception) {
            null
        }
    }

    private fun encodeImageToBase64(path: String): String? {
        return try {
            val file = java.io.File(path)
            if (!file.exists()) return null
            val bytes = file.readBytes()
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private fun createService(baseUrl: String): OpenAiService? {
        return try {
            val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.NONE
                })
                .build()
            Retrofit.Builder()
                .baseUrl(url)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenAiService::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
