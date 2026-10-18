package com.gift.tolife.core.datastore

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("gift_settings", Context.MODE_PRIVATE)

    // 设置是最新快照：慢订阅者只需最新值，避免连续即时操作让 tryEmit 失败。
    private val _settings = MutableSharedFlow<AppSettings>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val settings: SharedFlow<AppSettings> = _settings.asSharedFlow()

    init {
        _settings.tryEmit(readAll())
    }

    private fun readAll(): AppSettings {
        return AppSettings(
            apiKey = prefs.getString("api_key", "") ?: "",
            baseUrl = prefs.getString("base_url", "https://api.deepseek.com") ?: "https://api.deepseek.com",
            tagModel = prefs.getString("tag_model", "deepseek-chat") ?: "deepseek-chat",
            summaryModel = prefs.getString("summary_model", "deepseek-chat") ?: "deepseek-chat",
            visionModel = prefs.getString("vision_model", "deepseek-chat") ?: "deepseek-chat",
            // 2026-09-24：总结可以用另一个供应商；留空则跟随上面的默认供应商
            summaryApiKey = prefs.getString("summary_api_key", "") ?: "",
            summaryBaseUrl = prefs.getString("summary_base_url", "") ?: "",
            autoSummaryEnabled = prefs.getBoolean("auto_summary_enabled", true),
            summaryThinking = prefs.getString("summary_thinking", "high") ?: "high",
            biometricEnabled = prefs.getBoolean("biometric_enabled", false),
            themeMode = prefs.getString("theme_mode", "system") ?: "system",
            themeVariant = prefs.getString("theme_variant", "GREEN") ?: "GREEN"
        )
    }

    /** 已整体验证的 AI 文本配置：一次写入、一次通知，不暴露中间状态。 */
    fun updateAiConfiguration(settings: AppSettings) {
        prefs.edit()
            .putString("api_key", settings.apiKey)
            .putString("base_url", settings.baseUrl)
            .putString("tag_model", settings.tagModel)
            .putString("vision_model", settings.visionModel)
            .putString("summary_model", settings.summaryModel)
            .putString("summary_api_key", settings.summaryApiKey)
            .putString("summary_base_url", settings.summaryBaseUrl)
            .apply()
        _settings.tryEmit(readAll())
    }

    fun updateApiKey(key: String) {
        prefs.edit().putString("api_key", key).apply()
        _settings.tryEmit(readAll())
    }

    fun updateBaseUrl(url: String) {
        prefs.edit().putString("base_url", url).apply()
        _settings.tryEmit(readAll())
    }

    fun updateTagModel(model: String) {
        prefs.edit().putString("tag_model", model).apply()
        _settings.tryEmit(readAll())
    }

    fun updateSummaryThinking(value: String) {
        prefs.edit().putString("summary_thinking", value).apply()
        _settings.tryEmit(readAll())
    }

    fun updateSummaryModel(model: String) {
        prefs.edit().putString("summary_model", model).apply()
        _settings.tryEmit(readAll())
    }

    fun updateVisionModel(model: String) {
        prefs.edit().putString("vision_model", model).apply()
        _settings.tryEmit(readAll())
    }

    /** 总结专用供应商；两者都留空表示"跟随默认供应商"。 */
    fun updateSummaryProvider(apiKey: String, baseUrl: String) {
        prefs.edit()
            .putString("summary_api_key", apiKey)
            .putString("summary_base_url", baseUrl)
            .apply()
        _settings.tryEmit(readAll())
    }

    fun updateAutoSummaryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_summary_enabled", enabled).apply()
        _settings.tryEmit(readAll())
    }

    fun updateBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("biometric_enabled", enabled).apply()
        _settings.tryEmit(readAll())
    }

    fun updateThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
        _settings.tryEmit(readAll())
    }

    fun updateThemeVariant(variant: String) {
        prefs.edit().putString("theme_variant", variant).apply()
        _settings.tryEmit(readAll())
    }

    fun getLastActiveDay(): Long = prefs.getLong("last_active_day", 0L)

    fun getStreakCount(): Int = prefs.getInt("streak_count", 0)

    fun updateStreak(lastActiveDay: Long, streak: Int) {
        prefs.edit().putLong("last_active_day", lastActiveDay).putInt("streak_count", streak).apply()
    }
}
