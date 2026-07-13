package com.gift.tolife.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gift.tolife.core.datastore.AppSettings
import com.gift.tolife.core.datastore.SettingsDataStore
import com.gift.tolife.core.export.ExportImportManager
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
    private val chatClient: AiClient,
    private val exportManager: ExportImportManager
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

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    fun exportData(uri: Uri) {
        viewModelScope.launch {
            try {
                val count = exportManager.exportToUri(uri)
                _events.emit(SettingsEvent.ShowMessage("已导出 $count 条记录"))
            } catch (t: Throwable) {
                _events.emit(SettingsEvent.ShowMessage("导出失败: ${t.message ?: "未知错误"}"))
            }
        }
    }

    fun importData(uri: Uri) {
        viewModelScope.launch {
            try {
                val count = exportManager.importFromUri(uri)
                _events.emit(SettingsEvent.ShowMessage("已导入 $count 条记录"))
            } catch (t: Throwable) {
                _events.emit(SettingsEvent.ShowMessage("导入失败: ${t.message ?: "未知错误"}"))
            }
        }
    }

    fun testTagModel() {
        if (_uiState.value.testingTag) return
        _uiState.update { it.copy(testingTag = true, testResultTag = null) }
        viewModelScope.launch {
            try {
                val current = _uiState.value.settings
                val result = chatClient.chat(current.tagModel, "你是一个助手。", "回复：ok")
                _uiState.update {
                    it.copy(
                        testingTag = false,
                        testResultTag = if (!result.isNullOrBlank()) "✓ 连接成功" else "✗ 连接失败"
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        testingTag = false,
                        testResultTag = "✗ ${t.message ?: "连接失败"}"
                    )
                }
            }
        }
    }

    fun testSummaryModel() {
        if (_uiState.value.testingSummary) return
        _uiState.update { it.copy(testingSummary = true, testResultSummary = null) }
        viewModelScope.launch {
            try {
                val current = _uiState.value.settings
                val result = chatClient.chat(current.summaryModel, "你是一个助手。", "回复：ok")
                _uiState.update {
                    it.copy(
                        testingSummary = false,
                        testResultSummary = if (!result.isNullOrBlank()) "✓ 连接成功" else "✗ 连接失败"
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        testingSummary = false,
                        testResultSummary = "✗ ${t.message ?: "连接失败"}"
                    )
                }
            }
        }
    }

    fun testVisionModel() {
        if (_uiState.value.testingVision) return
        _uiState.update { it.copy(testingVision = true, testResultVision = null) }
        viewModelScope.launch {
            try {
                val current = _uiState.value.settings
                val result = chatClient.chat(current.visionModel, "你是一个助手。", "回复：ok")
                _uiState.update {
                    it.copy(
                        testingVision = false,
                        testResultVision = if (!result.isNullOrBlank()) "✓ 连接成功" else "✗ 连接失败"
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        testingVision = false,
                        testResultVision = "✗ ${t.message ?: "连接失败"}"
                    )
                }
            }
        }
    }
}

sealed class SettingsEvent {
    data class ShowMessage(val message: String) : SettingsEvent()
}
