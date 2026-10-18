package com.gift.tolife.feature.settings

import com.gift.tolife.core.network.BaseUrlValidator
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** 模型配置（.json）解析结果；API Key 永不在其中。 */
data class ModelConfigValues(
    val baseUrl: String,
    val tagModel: String,
    val summaryModel: String,
    val visionModel: String,
    /** 总结专用地址；空串表示跟随 [baseUrl]（2026-09-24 新增，v3 格式）。 */
    val summaryBaseUrl: String = ""
)

/**
 * 解析并校验模型配置 JSON，兼容 v3 / v2 [ModelConfigExport] 与旧版 [AppSettings] 两种历史格式。
 *
 * 全部字段校验通过才返回，由调用方统一应用——避免出现 baseUrl 已改、模型没改的"部分生效"。
 * 失败抛 [IllegalArgumentException]，消息可直接展示给用户。
 */
object ModelConfigCodec {

    const val MAX_CONFIG_BYTES = 64L * 1024
    const val CURRENT_VERSION = 3

    fun parse(json: String): ModelConfigValues {
        val root = try {
            JsonParser().parse(json)
        } catch (t: Exception) {
            throw IllegalArgumentException("不是有效的 JSON")
        }
        if (!root.isJsonObject) throw IllegalArgumentException("模型配置格式不正确")
        val obj = root.asJsonObject
        val version = numberField(obj, "version")
        if (version != null && version > CURRENT_VERSION) {
            throw IllegalArgumentException("模型配置版本不支持: $version")
        }
        val baseUrl = stringField(obj, "baseUrl") ?: throw IllegalArgumentException("缺少 baseUrl")
        val tagModel = stringField(obj, "tagModel") ?: throw IllegalArgumentException("缺少 tagModel")
        val summaryModel = stringField(obj, "summaryModel") ?: throw IllegalArgumentException("缺少 summaryModel")
        val visionModel = stringField(obj, "visionModel") ?: throw IllegalArgumentException("缺少 visionModel")
        val normalized = try {
            BaseUrlValidator.normalize(baseUrl)
        } catch (t: IllegalArgumentException) {
            throw IllegalArgumentException("API 地址无效: ${t.message}")
        }
        // v3：可选。没写 = 地址跟随日常识别；导入不覆盖草稿里的两组 Key。
        val summaryBaseUrlRaw = stringField(obj, "summaryBaseUrl").orEmpty()
        val summaryBaseUrl = if (summaryBaseUrlRaw.isEmpty()) {
            ""
        } else {
            try {
                BaseUrlValidator.normalize(summaryBaseUrlRaw)
            } catch (t: IllegalArgumentException) {
                throw IllegalArgumentException("总结服务地址无效: ${t.message}")
            }
        }
        return ModelConfigValues(
            baseUrl = normalized,
            tagModel = tagModel,
            summaryModel = summaryModel,
            visionModel = visionModel,
            summaryBaseUrl = summaryBaseUrl
        )
    }

    private fun stringField(obj: JsonObject, name: String): String? =
        obj.get(name)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun numberField(obj: JsonObject, name: String): Int? =
        obj.get(name)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asInt
}
