package com.gift.tolife.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gift.tolife.core.datastore.AppSettings
import com.gift.tolife.core.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isSaved: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
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
        viewModelScope.launch {
            settingsDataStore.updateApiKey(key)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun updateBaseUrl(url: String) {
        viewModelScope.launch {
            settingsDataStore.updateBaseUrl(url)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun updateTagModel(model: String) {
        viewModelScope.launch {
            settingsDataStore.updateTagModel(model)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun updateSummaryModel(model: String) {
        viewModelScope.launch {
            settingsDataStore.updateSummaryModel(model)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun updateVisionModel(model: String) {
        viewModelScope.launch {
            settingsDataStore.updateVisionModel(model)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun clearSavedFlag() {
        _uiState.update { it.copy(isSaved = false) }
    }
}
