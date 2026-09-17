package com.gift.tolife.feature.settings

import com.gift.tolife.core.network.BaseUrlValidator
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** 模型配置（.json）解析结果；API Key 永不在其中。 */
data class ModelConfigValues(
    val baseUrl: String,
    val tagModel: String,
    val summaryModel: String,
    val visionModel: String
)

/**
 * 解析并校验模型配置 JSON，兼容新版 [ModelConfigExport] 与旧版 [AppSettings] 两种格式。
 *
 * 全部字段校验通过才返回，由调用方统一应用——避免出现 baseUrl 已改、模型没改的"部分生效"。
 * 失败抛 [IllegalArgumentException]，消息可直接展示给用户。
 */
object ModelConfigCodec {

    const val MAX_CONFIG_BYTES = 64L * 1024

    fun parse(json: String): ModelConfigValues {
        val root = try {
            JsonParser().parse(json)
        } catch (t: Exception) {
            throw IllegalArgumentException("不是有效的 JSON")
        }
        if (!root.isJsonObject) throw IllegalArgumentException("模型配置格式不正确")
        val obj = root.asJsonObject
        val version = numberField(obj, "version")
        if (version != null && version > 2) {
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
        return ModelConfigValues(
            baseUrl = normalized,
            tagModel = tagModel,
            summaryModel = summaryModel,
            visionModel = visionModel
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
