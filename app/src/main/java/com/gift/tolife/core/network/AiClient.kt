package com.gift.tolife.core.network

import com.gift.tolife.core.ai.AiResult
import com.gift.tolife.core.ai.ThinkingPolicy
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
    // 2026-09-24：总结单次生成实测 10~180s（思考模型更久），60s 读超时会把正常请求判成失败。
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
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

    suspend fun chat(
        model: String,
        systemPrompt: String,
        userMessage: String,
        disableThinking: Boolean = true,
        jsonMode: Boolean = false,
        maxTokens: Int = 2048,
        temperature: Double = 0.3,
        thinkingPolicy: ThinkingPolicy = if (disableThinking) ThinkingPolicy.OFF else ThinkingPolicy.HIGH_BOUNDED,
    ): AiResult<String> {
        val settings = settingsDataStore.settings.first()
        return chatWith(model, systemPrompt, userMessage, settings.apiKey, settings.baseUrl,
            jsonMode, maxTokens, temperature, thinkingPolicy)
    }

    suspend fun chatWith(
        model: String,
        systemPrompt: String,
        userMessage: String,
        apiKey: String,
        baseUrl: String,
        jsonMode: Boolean = false,
        maxTokens: Int = 2048,
        temperature: Double = 0.3,
        thinkingPolicy: ThinkingPolicy = ThinkingPolicy.OFF,
    ): AiResult<String> {
        return try {
            if (apiKey.isBlank()) return AiResult.PermanentFailure("API Key not configured")
            val service = getService(baseUrl) ?: return AiResult.PermanentFailure("Invalid base URL")
            val request = ChatRequest(
                model = model,
                messages = listOf(
                    Message(role = "system", content = systemPrompt),
                    Message(role = "user", content = userMessage)
                ),
                temperature = temperature,
                max_tokens = maxTokens,
                // 关思考只用 enable_thinking（SiliconFlow/Qwen 认这个）；
                // 开思考则三种参数同时发，谁认哪个由服务商决定，不认的在 400 降级梯子里丢掉。
                enable_thinking = if (thinkingPolicy.enabled) null else false,
                reasoning_effort = thinkingPolicy.effort.takeIf { thinkingPolicy.enabled },
                thinking = if (thinkingPolicy.enabled) ThinkingFlag("enabled") else null,
                thinking_budget = thinkingPolicy.budgetTokens.takeIf { thinkingPolicy.enabled },
                response_format = if (jsonMode) ResponseFormat.JSON_OBJECT else null
            )
            val response = service.chatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )
            val choice = response.choices?.firstOrNull()
            val msg = choice?.message
            val result = msg?.content?.takeIf { it.isNotBlank() }
            when {
                result != null -> AiResult.Success(result)
                // 2026-09-25：以前这里会回落到 reasoning_content，结果是**思考过程被当成正文**
                // 渲染成卡片（线上复现：卡片里出现 "Card 3 (insight): … Let me count …"）。
                // 思考不是答案，交给调用方决定能否从里面捞出结构化内容，绝不直接展示。
                (msg?.reasoning_content?.isNotBlank() == true) -> AiResult.ReasoningOnly(
                    reasoning = msg.reasoning_content,
                    finishReason = choice?.finish_reason,
                )
                choice?.finish_reason == "length" -> AiResult.PermanentFailure("模型输出被截断（达到输出上限）")
                else -> AiResult.PermanentFailure("模型没有输出内容")
            }
        } catch (e: java.net.UnknownHostException) {
            AiResult.RetryableFailure(e)
        } catch (e: java.net.SocketTimeoutException) {
            AiResult.RetryableFailure(e)
        } catch (e: java.io.IOException) {
            AiResult.RetryableFailure(e)
        } catch (e: HttpException) {
            when (e.code()) {
                401, 403 -> AiResult.PermanentFailure("API Key 无效或无权访问（HTTP ${e.code()}）", e.code())
                404 -> AiResult.PermanentFailure("模型或地址不存在（HTTP 404）", e.code())
                408, 429, in 500..599 -> AiResult.RetryableFailure(e)
                // 2026-09-24：以前这里只回 "HTTP 400"，把服务端的真正原因（例如
                // {"code":20012,"message":"Model does not exist"}）丢掉了，线上没法排查
                else -> AiResult.PermanentFailure("HTTP ${e.code()}：${serverMessage(e)}", e.code())
            }
        } catch (e: IllegalArgumentException) {
            AiResult.PermanentFailure("配置错误: ${e.message}")
        } catch (e: com.google.gson.JsonParseException) {
            AiResult.PermanentFailure("响应解析错误: ${e.message}")
        } catch (t: Throwable) {
            retryableAiFailure(t)
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
            val choice = response.choices?.firstOrNull()
            val msg = choice?.message
            val result = msg?.content?.takeIf { it.isNotBlank() }
            when {
                result != null -> AiResult.Success(result)
                // 思考不是答案，绝不回落到 reasoning_content（同上）
                choice?.finish_reason == "length" -> AiResult.PermanentFailure("视觉模型输出被截断（达到输出上限）")
                msg?.reasoning_content?.isNotBlank() == true -> AiResult.PermanentFailure("视觉模型只输出了思考、没有给出描述")
                else -> AiResult.PermanentFailure("模型没有输出内容")
            }
        } catch (e: java.net.UnknownHostException) {
            AiResult.RetryableFailure(e)
        } catch (e: java.net.SocketTimeoutException) {
            AiResult.RetryableFailure(e)
        } catch (e: java.io.IOException) {
            AiResult.RetryableFailure(e)
        } catch (e: HttpException) {
            when (e.code()) {
                401, 403 -> AiResult.PermanentFailure("API Key 无效或无权访问（HTTP ${e.code()}）", e.code())
                408, 429, in 500..599 -> AiResult.RetryableFailure(e)
                else -> AiResult.PermanentFailure("HTTP ${e.code()}：${serverMessage(e)}", e.code())
            }
        } catch (e: IllegalArgumentException) {
            AiResult.PermanentFailure("配置错误: ${e.message}")
        } catch (e: com.google.gson.JsonParseException) {
            AiResult.PermanentFailure("响应解析错误: ${e.message}")
        } catch (t: Throwable) {
            retryableAiFailure(t)
        }
    }

    /** 取服务端错误响应体里的可读信息（截断，避免刷屏）。 */
    private fun serverMessage(e: HttpException): String {
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
        if (body.isBlank()) return "服务端没有给出原因"
        return body.replace(Regex("\\s+"), " ").trim().take(180)
    }

    private fun encodeImageToBase64(path: String): String? {
        return try {
            val file = java.io.File(path)
            if (!file.exists()) return null
            val bytes = file.readBytes()
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (t: Throwable) {
            rethrowUnrecoverableAiFailure(t)
            null
        }
    }
}
