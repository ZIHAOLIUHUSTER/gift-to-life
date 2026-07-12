package com.gift.tolife.feature.record

import com.gift.tolife.core.model.Entry

data class RecordUiState(
    val entries: List<Entry> = emptyList(),
    val isSearchMode: Boolean = false,
    val searchQuery: String = "",
    val selectedEntry: Entry? = null,
    val isLoading: Boolean = false
)

sealed class RecordEvent {
    data class ShowSnackbar(val message: String) : RecordEvent()
}
