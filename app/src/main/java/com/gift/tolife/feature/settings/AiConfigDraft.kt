package com.gift.tolife.feature.settings

import com.gift.tolife.core.datastore.AppSettings
import com.gift.tolife.core.network.BaseUrlValidator

enum class AiConfigField { BASE_URL, API_KEY, TAG_MODEL, VISION_MODEL, SUMMARY_MODEL, SUMMARY_BASE_URL, SUMMARY_API_KEY }
enum class AiModelTarget { TAG, VISION, SUMMARY }

/** 编辑、状态展示、导入与连接测试共用的文本草稿；即时开关不属于此草稿。 */
data class AiConfigDraft(
    val apiKey: String,
    val baseUrl: String,
    val tagModel: String,
    val visionModel: String,
    val summaryModel: String,
    val summaryApiKey: String,
    val summaryBaseUrl: String
) {
    val hasSummaryOverride: Boolean get() = summaryBaseUrl.isNotBlank() || summaryApiKey.isNotBlank()

    fun withField(field: AiConfigField, value: String): AiConfigDraft = when (field) {
        AiConfigField.BASE_URL -> copy(baseUrl = value)
        AiConfigField.API_KEY -> copy(apiKey = value)
        AiConfigField.TAG_MODEL -> copy(tagModel = value)
        AiConfigField.VISION_MODEL -> copy(visionModel = value)
        AiConfigField.SUMMARY_MODEL -> copy(summaryModel = value)
        AiConfigField.SUMMARY_BASE_URL -> copy(summaryBaseUrl = value)
        AiConfigField.SUMMARY_API_KEY -> copy(summaryApiKey = value)
    }

    /** 导入永不覆盖草稿里的任何 Key，也不改即时设置。 */
    fun importConfig(config: ModelConfigValues): AiConfigDraft = copy(
        baseUrl = config.baseUrl,
        tagModel = config.tagModel,
        visionModel = config.visionModel,
        summaryModel = config.summaryModel,
        summaryBaseUrl = config.summaryBaseUrl
    )

    fun applyTo(settings: AppSettings): AppSettings = settings.copy(
        apiKey = apiKey, baseUrl = baseUrl, tagModel = tagModel, visionModel = visionModel,
        summaryModel = summaryModel, summaryApiKey = summaryApiKey, summaryBaseUrl = summaryBaseUrl
    )

    companion object {
        fun from(settings: AppSettings) = AiConfigDraft(
            settings.apiKey, settings.baseUrl, settings.tagModel, settings.visionModel,
            settings.summaryModel, settings.summaryApiKey, settings.summaryBaseUrl
        )
    }
}

data class AiConfigValidation(
    val normalized: AiConfigDraft?,
    val errors: Map<AiConfigField, String>
)

data class AiDraftConnection(val model: String, val apiKey: String, val baseUrl: String)

object AiConfigValidator {
    /** 一次收集所有错误；只有整份草稿合法才提供可写入的配置。 */
    fun validate(draft: AiConfigDraft): AiConfigValidation {
        val errors = linkedMapOf<AiConfigField, String>()
        fun address(field: AiConfigField, raw: String, optional: Boolean = false): String {
            if (raw.isBlank()) {
                if (!optional) errors[field] = "请输入服务地址"
                return ""
            }
            return try {
                BaseUrlValidator.normalize(raw)
            } catch (e: IllegalArgumentException) {
                errors[field] = e.message ?: "服务地址无效"
                ""
            }
        }
        fun model(field: AiConfigField, raw: String): String = raw.trim().also {
            if (it.isEmpty()) errors[field] = "请输入模型名称"
        }
        val baseUrl = address(AiConfigField.BASE_URL, draft.baseUrl)
        val tag = model(AiConfigField.TAG_MODEL, draft.tagModel)
        val vision = model(AiConfigField.VISION_MODEL, draft.visionModel)
        val summary = model(AiConfigField.SUMMARY_MODEL, draft.summaryModel)
        val summaryUrl = address(AiConfigField.SUMMARY_BASE_URL, draft.summaryBaseUrl, optional = true)
        val normalized = if (errors.isEmpty()) draft.copy(
            apiKey = draft.apiKey.trim(), baseUrl = baseUrl, tagModel = tag,
            visionModel = vision, summaryModel = summary,
            summaryApiKey = draft.summaryApiKey.trim(), summaryBaseUrl = summaryUrl
        ) else null
        return AiConfigValidation(normalized, errors)
    }

    /** 整份验证通过才调用一次写入；异常直接交给调用方，不能报告成功。 */
    fun save(draft: AiConfigDraft, settings: AppSettings, write: (AppSettings) -> Unit): AiConfigValidation {
        val validation = validate(draft)
        validation.normalized?.let { write(it.applyTo(settings)) }
        return validation
    }

    /** 所有回退只取本次草稿；空 Key 可保存为仅本地状态，但连接测试需要 Key。 */
    fun connection(draft: AiConfigDraft, target: AiModelTarget): AiDraftConnection {
        val summary = target == AiModelTarget.SUMMARY
        val model = when (target) {
            AiModelTarget.TAG -> draft.tagModel
            AiModelTarget.VISION -> draft.visionModel
            AiModelTarget.SUMMARY -> draft.summaryModel
        }.trim()
        require(model.isNotEmpty()) { "请先填写模型名称" }
        val key = if (summary) draft.summaryApiKey.trim().ifBlank { draft.apiKey.trim() } else draft.apiKey.trim()
        require(key.isNotEmpty()) { "未配置 API Key，无法测试连接" }
        val url = if (summary) draft.summaryBaseUrl.ifBlank { draft.baseUrl } else draft.baseUrl
        require(url.isNotBlank()) { "请先填写服务地址" }
        return AiDraftConnection(model, key, BaseUrlValidator.normalize(url))
    }
}
