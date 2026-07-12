package com.gift.tolife.feature.record

import com.gift.tolife.core.model.Entry

data class RecordUiState(
    val entries: List<Entry> = emptyList(),
    val pendingImageUri: android.net.Uri? = null,
    val isSearchMode: Boolean = false,
    val searchQuery: String = "",
    val selectedEntry: Entry? = null,
    val editingImageEntryId: Long? = null,
    val isLoading: Boolean = false
)

sealed class RecordEvent {
    data class ShowSnackbar(val message: String) : RecordEvent()
}
