package com.gift.tolife.core.network

import com.gift.tolife.core.network.dto.ChatRequest
import com.gift.tolife.core.network.dto.ChatResponse
import com.gift.tolife.core.network.dto.VisionChatRequest
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
