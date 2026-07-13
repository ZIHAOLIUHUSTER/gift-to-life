package com.gift.tolife.core.network

import com.gift.tolife.core.network.dto.ChatRequest
import com.gift.tolife.core.network.dto.ChatResponse
import com.gift.tolife.core.network.dto.VisionMessage
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenAiService {
    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatRequest
    ): ChatResponse

    @POST("v1/chat/completions")
    suspend fun chatCompletionVision(
        @Header("Authorization") authorization: String,
        @Body request: VisionChatRequest
    ): ChatResponse
}

data class VisionChatRequest(
    val model: String,
    val messages: List<VisionMessage>,
    val temperature: Double = 0.3,
    val max_tokens: Int = 300,
    val enable_thinking: Boolean? = null
)
