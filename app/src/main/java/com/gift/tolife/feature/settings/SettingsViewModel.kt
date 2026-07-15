package com.gift.tolife.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import com.gift.tolife.core.ai.AiResult
import com.gift.tolife.core.datastore.AppSettings
import com.gift.tolife.core.datastore.SettingsDataStore
import com.gift.tolife.core.database.EntryRepository
import com.gift.tolife.core.database.dao.EntryDao
import com.gift.tolife.core.export.ExportImportManager
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.network.AiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
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
    private val exportManager: ExportImportManager,
    private val repository: EntryRepository,
    @ApplicationContext private val context: Context,
    private val entryDao: EntryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    data class StatsData(
        val monthlyCount: Int = 0,
        val streakCount: Int = 0,
        val dailyCounts: Map<Int, Int> = emptyMap()
    )

    private val _stats = MutableStateFlow(StatsData())
    val stats: StateFlow<StatsData> = _stats.asStateFlow()

    data class DataStats(
        val totalEntries: Int = 0,
        val usageDays: Int = 0
    )

    private val _dataStats = MutableStateFlow(DataStats())
    val dataStats: StateFlow<DataStats> = _dataStats.asStateFlow()

    fun refreshDataStats() {
        viewModelScope.launch {
            val total = entryDao.getTotalEntryCount()
            val firstTimestamp = entryDao.getFirstEntryTimestamp()
            val days = if (firstTimestamp != null) {
                val elapsed = System.currentTimeMillis() - firstTimestamp
                (elapsed / (24 * 60 * 60 * 1000)).toInt() + 1
            } else 0
            _dataStats.value = DataStats(totalEntries = total, usageDays = days)
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)

            val startCal = Calendar.getInstance().apply {
                set(year, month, 1, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val endCal = Calendar.getInstance().apply {
                set(year, month, getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59)
                set(Calendar.MILLISECOND, 999)
            }

            val count = entryDao.getMonthlyCount(startCal.timeInMillis, endCal.timeInMillis)
            val timestamps = entryDao.getMonthlyTimestamps(startCal.timeInMillis, endCal.timeInMillis)

            val dailyCounts = mutableMapOf<Int, Int>()
            val localCal = Calendar.getInstance()
            timestamps.forEach { ts ->
                localCal.timeInMillis = ts
                val day = localCal.get(Calendar.DAY_OF_MONTH)
                dailyCounts[day] = (dailyCounts[day] ?: 0) + 1
            }

            _stats.value = StatsData(
                monthlyCount = count,
                streakCount = settingsDataStore.getStreakCount(),
                dailyCounts = dailyCounts
            )
        }
    }

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

    fun updateThemeMode(mode: String) {
        settingsDataStore.updateThemeMode(mode)
        _uiState.update { it.copy(isSaved = true) }
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
                val count = exportManager.replaceImportFromUri(uri)
                _events.emit(SettingsEvent.ShowMessage("已导入 $count 条记录"))
            } catch (t: Throwable) {
                _events.emit(SettingsEvent.ShowMessage("导入失败: ${t.message ?: "未知错误"}"))
            }
        }
    }

    fun testTagModel(model: String) {
        if (_uiState.value.testingTag) return
        _uiState.update { it.copy(testingTag = true, testResultTag = null) }
        viewModelScope.launch {
            try {
                val result = chatClient.chat(model, "你是一个助手。", "回复：ok")
                _uiState.update {
                    it.copy(testingTag = false, testResultTag = when (result) {
                        is AiResult.Success -> "✓ 连接成功"
                        is AiResult.PermanentFailure -> "✗ ${result.message}"
                        is AiResult.RetryableFailure -> "✗ 连接失败"
                    })
                }
            } catch (t: Throwable) {
                _uiState.update { it.copy(testingTag = false, testResultTag = "✗ ${t.message ?: "连接失败"}") }
            }
        }
    }

    fun testSummaryModel(model: String) {
        if (_uiState.value.testingSummary) return
        _uiState.update { it.copy(testingSummary = true, testResultSummary = null) }
        viewModelScope.launch {
            try {
                val result = chatClient.chat(model, "你是一个助手。", "回复：ok")
                _uiState.update {
                    it.copy(testingSummary = false, testResultSummary = when (result) {
                        is AiResult.Success -> "✓ 连接成功"
                        is AiResult.PermanentFailure -> "✗ ${result.message}"
                        is AiResult.RetryableFailure -> "✗ 连接失败"
                    })
                }
            } catch (t: Throwable) {
                _uiState.update { it.copy(testingSummary = false, testResultSummary = "✗ ${t.message ?: "连接失败"}") }
            }
        }
    }

    fun testVisionModel(model: String) {
        if (_uiState.value.testingVision) return
        _uiState.update { it.copy(testingVision = true, testResultVision = null) }
        viewModelScope.launch {
            try {
                val result = chatClient.chat(model, "你是一个助手。", "回复：ok")
                _uiState.update {
                    it.copy(testingVision = false, testResultVision = when (result) {
                        is AiResult.Success -> "✓ 连接成功"
                        is AiResult.PermanentFailure -> "✗ ${result.message}"
                        is AiResult.RetryableFailure -> "✗ 连接失败"
                    })
                }
            } catch (t: Throwable) {
                _uiState.update { it.copy(testingVision = false, testResultVision = "✗ ${t.message ?: "连接失败"}") }
            }
        }
    }

    // 回收站相关
    suspend fun getDeletedEntries(): List<Entry> {
        return repository.getDeletedEntries()
    }

    suspend fun restoreEntry(id: Long) {
        repository.restoreEntry(id)
    }

    suspend fun permanentlyDeleteAll() {
        repository.permanentlyDeleteAllDeleted()
    }

    fun exportModelConfig(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val s = _uiState.value.settings
                val config = ModelConfigExport(
                    baseUrl = s.baseUrl,
                    tagModel = s.tagModel,
                    summaryModel = s.summaryModel,
                    visionModel = s.visionModel
                )
                val json = GsonBuilder().setPrettyPrinting().create().toJson(config)
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                _events.emit(SettingsEvent.ShowMessage("模型配置已导出（不含 API Key）"))
            } catch (t: Throwable) {
                _events.emit(SettingsEvent.ShowMessage("导出失败: ${t.message ?: ""}"))
            }
        }
    }

    fun importModelConfig(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) }
                    ?: return@launch
                // 尝试新格式 ModelConfigExport
                val newConfig = try { Gson().fromJson(json, ModelConfigExport::class.java) } catch (_: Exception) { null }
                if (newConfig != null) {
                    settingsDataStore.updateBaseUrl(newConfig.baseUrl)
                    settingsDataStore.updateTagModel(newConfig.tagModel)
                    settingsDataStore.updateSummaryModel(newConfig.summaryModel)
                    settingsDataStore.updateVisionModel(newConfig.visionModel)
                } else {
                    // 回退到旧 AppSettings 格式
                    val oldConfig = Gson().fromJson(json, AppSettings::class.java) ?: return@launch
                    settingsDataStore.updateApiKey(oldConfig.apiKey)
                    settingsDataStore.updateBaseUrl(oldConfig.baseUrl)
                    settingsDataStore.updateTagModel(oldConfig.tagModel)
                    settingsDataStore.updateSummaryModel(oldConfig.summaryModel)
                    settingsDataStore.updateVisionModel(oldConfig.visionModel)
                }
                _events.emit(SettingsEvent.ShowMessage("模型配置已导入"))
            } catch (t: Throwable) {
                _events.emit(SettingsEvent.ShowMessage("导入失败: ${t.message ?: ""}"))
            }
        }
    }
}

data class ModelConfigExport(
    val version: Int = 2,
    val baseUrl: String,
    val tagModel: String,
    val summaryModel: String,
    val visionModel: String
)

sealed class SettingsEvent {
    data class ShowMessage(val message: String) : SettingsEvent()
}
