package com.gift.tolife.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val API_KEY = stringPreferencesKey("api_key")
        val BASE_URL = stringPreferencesKey("base_url")
        val TAG_MODEL = stringPreferencesKey("tag_model")
        val SUMMARY_MODEL = stringPreferencesKey("summary_model")
        val VISION_MODEL = stringPreferencesKey("vision_model")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            apiKey = prefs[Keys.API_KEY] ?: "",
            baseUrl = prefs[Keys.BASE_URL] ?: "https://api.deepseek.com",
            tagModel = prefs[Keys.TAG_MODEL] ?: "deepseek-chat",
            summaryModel = prefs[Keys.SUMMARY_MODEL] ?: "deepseek-chat",
            visionModel = prefs[Keys.VISION_MODEL] ?: "deepseek-chat",
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false
        )
    }

    suspend fun updateApiKey(key: String) {
        context.settingsDataStore.edit { it[Keys.API_KEY] = key }
    }

    suspend fun updateBaseUrl(url: String) {
        context.settingsDataStore.edit { it[Keys.BASE_URL] = url }
    }

    suspend fun updateTagModel(model: String) {
        context.settingsDataStore.edit { it[Keys.TAG_MODEL] = model }
    }

    suspend fun updateSummaryModel(model: String) {
        context.settingsDataStore.edit { it[Keys.SUMMARY_MODEL] = model }
    }

    suspend fun updateVisionModel(model: String) {
        context.settingsDataStore.edit { it[Keys.VISION_MODEL] = model }
    }

    suspend fun updateBiometricEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }
}
