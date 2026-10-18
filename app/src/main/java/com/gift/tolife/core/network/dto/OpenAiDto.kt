package com.gift.tolife.core.network.dto

/**
 * 结构化输出约束（OpenAI 兼容）。
 *
 * 2026-09-24：总结改用 JSON 卡片契约后必须带上这个字段——实测不加时
 * 服务端有约 50% 概率把 JSON 包在 ``` 围栏里，偶发非法 JSON 与超时。
 */
data class ResponseFormat(val type: String) {
    companion object {
        val JSON_OBJECT = ResponseFormat("json_object")
    }
}

/** DeepSeek 官方 / 部分中转的思考开关：{"type":"enabled"}。 */
data class ThinkingFlag(val type: String)

data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double = 0.3,
    val max_tokens: Int = 2048,
    val enable_thinking: Boolean? = null,  // SiliconFlow / Qwen 风格思考开关
    val reasoning_effort: String? = null,  // DeepSeek/OpenAI 风格思考强度：low / high / max
    val thinking: ThinkingFlag? = null,    // DeepSeek 官方风格开关
    val thinking_budget: Int? = null,      // SiliconFlow 风格：思维链 token 上限
    val response_format: ResponseFormat? = null  // Gson 默认忽略 null 字段，不影响其他调用
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
    val message: Message? = null,
    /** "stop" / "length"；length 表示输出被截断。 */
    val finish_reason: String? = null
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

data class VisionChatRequest(
    val model: String,
    val messages: List<VisionMessage>,
    val temperature: Double = 0.3,
    val max_tokens: Int = 300,
    val enable_thinking: Boolean? = null
)
