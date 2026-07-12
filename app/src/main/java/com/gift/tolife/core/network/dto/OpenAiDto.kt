package com.gift.tolife.core.network.dto

data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double = 0.3,
    val max_tokens: Int = 300
)

data class Message(
    val role: String,
    val content: String
)

data class ChatResponse(
    val choices: List<Choice>? = null
)

data class Choice(
    val message: Message? = null
)

// Vision message with image support (OpenAI compatible format)
data class VisionMessage(
    val role: String,
    val content: List<ContentPart>
)

data class ContentPart(
    val type: String,
    val text: String? = null,
    val image_url: ImageUrl? = null
)

data class ImageUrl(
    val url: String
)
