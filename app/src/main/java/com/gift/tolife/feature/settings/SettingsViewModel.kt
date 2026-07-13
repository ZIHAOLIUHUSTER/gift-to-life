package com.gift.tolife.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gift.tolife.core.datastore.AppSettings
import com.gift.tolife.core.datastore.SettingsDataStore
import com.gift.tolife.core.network.AiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isSaved: Boolean = false,
    val testingTag: Boolean = false,
    val testingSummary: Boolean = false,
    val testingVision: Boolean = false,
    val testResultTag: String? = null,
    val testResultSummary: String? = null,
    val testResultVision: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val chatClient: AiClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    fun updateApiKey(key: String) {
        settingsDataStore.updateApiKey(key)
        _uiState.update { it.copy(isSaved = true) }
    }

    fun updateBaseUrl(url: String) {
        settingsDataStore.updateBaseUrl(url)
        _uiState.update { it.copy(isSaved = true) }
    }

    fun updateTagModel(model: String) {
        settingsDataStore.updateTagModel(model)
        _uiState.update { it.copy(isSaved = true) }
    }

    fun updateSummaryModel(model: String) {
        settingsDataStore.updateSummaryModel(model)
        _uiState.update { it.copy(isSaved = true) }
    }

    fun updateVisionModel(model: String) {
        settingsDataStore.updateVisionModel(model)
        _uiState.update { it.copy(isSaved = true) }
    }

    fun clearSavedFlag() {
        _uiState.update { it.copy(isSaved = false) }
    }

    fun testTagModel() {
        if (_uiState.value.testingTag) return
        _uiState.update { it.copy(testingTag = true, testResultTag = null) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            _uiState.update {
                it.copy(
                    testingTag = false,
                    testResultTag = "✓ 连接成功（模拟）"
                )
            }
        }
    }

    fun testSummaryModel() {
        if (_uiState.value.testingSummary) return
        _uiState.update { it.copy(testingSummary = true, testResultSummary = null) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            _uiState.update {
                it.copy(
                    testingSummary = false,
                    testResultSummary = "✓ 连接成功（模拟）"
                )
            }
        }
    }

    fun testVisionModel() {
        if (_uiState.value.testingVision) return
        _uiState.update { it.copy(testingVision = true, testResultVision = null) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            _uiState.update {
                it.copy(
                    testingVision = false,
                    testResultVision = "✓ 连接成功（模拟）"
                )
            }
        }
    }
}
