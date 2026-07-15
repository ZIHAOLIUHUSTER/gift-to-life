package com.gift.tolife.core.datastore

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
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

    private val _settings = MutableSharedFlow<AppSettings>(replay = 1)
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
            biometricEnabled = prefs.getBoolean("biometric_enabled", false),
            themeMode = prefs.getString("theme_mode", "system") ?: "system"
        )
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

    fun updateSummaryModel(model: String) {
        prefs.edit().putString("summary_model", model).apply()
        _settings.tryEmit(readAll())
    }

    fun updateVisionModel(model: String) {
        prefs.edit().putString("vision_model", model).apply()
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
}
