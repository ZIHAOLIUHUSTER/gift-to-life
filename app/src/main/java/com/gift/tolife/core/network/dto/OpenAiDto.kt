package com.gift.tolife.core.network.dto

data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double = 0.3,
    val max_tokens: Int = 1024,
    val enable_thinking: Boolean? = null  // DeepSeek/SiliconFlow 思考模式开关
)

data class Message(
    val role: String,
    val content: String? = null,
    val reasoning_content: String? = null
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
