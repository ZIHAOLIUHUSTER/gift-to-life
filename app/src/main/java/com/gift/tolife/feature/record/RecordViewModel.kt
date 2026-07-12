package com.gift.tolife.feature.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gift.tolife.core.database.EntryRepository
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.EntryType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordViewModel @Inject constructor(
    private val repository: EntryRepository
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
            val entry = Entry(
                content = content.trim(),
                type = EntryType.NORMAL
            )
            repository.save(entry)
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

    fun getFilteredEntries(): List<Entry> {
        val state = _uiState.value
        return if (state.searchQuery.isBlank()) {
            state.entries
        } else {
            state.entries.filter {
                it.content.contains(state.searchQuery, ignoreCase = true)
            }
        }
    }
}
