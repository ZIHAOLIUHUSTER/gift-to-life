package com.gift.tolife.feature.record

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gift.tolife.core.common.ImageUtil
import com.gift.tolife.core.database.EntryRepository
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.EntryType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordViewModel @Inject constructor(
    private val repository: EntryRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RecordEvent>()
    val events: SharedFlow<RecordEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.getAllEntries().collect { entries ->
                _uiState.update { it.copy(entries = entries, isLoading = false) }
            }
        }
    }

    fun save(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            val currentState = _uiState.value
            val imagePath = currentState.pendingImageUri?.let { uri ->
                ImageUtil.copyToPrivateDir(context, uri)
            }

            val entry = Entry(
                content = content.trim(),
                imagePath = imagePath,
                type = EntryType.NORMAL
            )
            repository.save(entry)
            _uiState.update { it.copy(pendingImageUri = null) }
        }
    }

    fun selectImage(uri: android.net.Uri) {
        _uiState.update { it.copy(pendingImageUri = uri) }
    }

    fun clearImage() {
        _uiState.update { it.copy(pendingImageUri = null) }
    }

    fun setEditingImage(entryId: Long?) {
        _uiState.update { it.copy(editingImageEntryId = entryId) }
    }

    fun removeImage(entry: Entry) {
        viewModelScope.launch {
            repository.update(entry.copy(imagePath = null))
        }
    }

    fun replaceImage(entry: Entry, uri: android.net.Uri) {
        viewModelScope.launch {
            val imagePath = ImageUtil.copyToPrivateDir(context, uri)
            if (imagePath != null) {
                repository.update(entry.copy(imagePath = imagePath))
                _uiState.update { it.copy(editingImageEntryId = null) }
            }
        }
    }

    fun update(entry: Entry) {
        viewModelScope.launch {
            repository.update(entry)
            _uiState.update { it.copy(selectedEntry = null) }
        }
    }

    fun delete(entry: Entry) {
        viewModelScope.launch {
            repository.delete(entry)
            _events.emit(RecordEvent.ShowSnackbar("已删除"))
        }
    }

    fun selectEntry(entry: Entry) {
        _uiState.update { it.copy(selectedEntry = entry) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedEntry = null) }
    }

    fun toggleSearch() {
        _uiState.update {
            if (it.isSearchMode) {
                it.copy(isSearchMode = false, searchQuery = "")
            } else {
                it.copy(isSearchMode = true, searchQuery = "")
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }
}
