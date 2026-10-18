package com.gift.tolife.core.datastore

/**
 * 全局设置。
 *
 * 2026-09-24：供应商拆成两组——
 * - 默认供应商（[apiKey] / [baseUrl]）：标签模型 + 视觉模型（后台自动跑，追求便宜快速）
 * - 总结供应商（[summaryApiKey] / [summaryBaseUrl]）：周志/月志（追求文本质量，可以用另一个服务商）
 * 总结供应商留空时**自动跟随**默认供应商，老用户的单供应商配置无需改动。
 */
data class AppSettings(
    val apiKey: String = "",
    val baseUrl: String = "https://api.deepseek.com",
    val tagModel: String = "deepseek-chat",
    val summaryModel: String = "deepseek-chat",
    val visionModel: String = "deepseek-chat",
    /** 总结专用 API Key；留空跟随 [apiKey]。 */
    val summaryApiKey: String = "",
    /** 总结专用服务地址；留空跟随 [baseUrl]。 */
    val summaryBaseUrl: String = "",
    /** 每周一/每月初自动生成回顾并通知。 */
    val autoSummaryEnabled: Boolean = true,
    /**
     * 总结的思考强度：off（不思考，最快）/ high（默认：思考 + 上限 2048）/ deep（不设上限，最慢）。
     * 见 [com.gift.tolife.core.ai.ThinkingPolicy]。
     */
    val summaryThinking: String = "high",
    val biometricEnabled: Boolean = false,
    val themeMode: String = "system",
    /** To Life 系列皮肤（ThemeVariant.name）；Gift 默认青松。 */
    val themeVariant: String = "GREEN"
) {
    val effectiveSummaryApiKey: String get() = summaryApiKey.ifBlank { apiKey }

    val effectiveSummaryBaseUrl: String get() = summaryBaseUrl.ifBlank { baseUrl }

    /** 是否配置了独立的总结供应商（设置页据此显示"跟随"提示）。 */
    val hasSeparateSummaryProvider: Boolean get() = summaryBaseUrl.isNotBlank()
}
