package com.gift.tolife.core.datastore

data class AppSettings(
    val apiKey: String = "",
    val baseUrl: String = "https://api.deepseek.com",
    val tagModel: String = "deepseek-chat",
    val summaryModel: String = "deepseek-chat",
    val visionModel: String = "deepseek-chat",
    val biometricEnabled: Boolean = false,
    val themeMode: String = "system",
    /** To Life 系列皮肤（ThemeVariant.name）；Gift 默认青松。 */
    val themeVariant: String = "GREEN"
)
