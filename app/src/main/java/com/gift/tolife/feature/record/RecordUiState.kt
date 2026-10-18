package com.gift.tolife.feature.record

import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.EntryQuery
import com.gift.tolife.core.common.SharedContent

data class PendingShare(val id: Long, val content: SharedContent)

data class RecordUiState(
    val draftText: String = "",
    val draftVersion: Long = 0,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val pendingImageUri: android.net.Uri? = null,
    val isSearchMode: Boolean = false,
    val searchQuery: String = "",
    val selectedEntry: Entry? = null,
    val entryQuery: EntryQuery = EntryQuery(),
    val editingImageEntry: Entry? = null,
    val editingTagsEntryId: Long? = null,
    val isSavingEdit: Boolean = false,
    val pendingShares: List<PendingShare> = emptyList(),
    val composerFocusRequest: Long? = null
)

sealed class RecordEvent {
    data class ShowSnackbar(val message: String) : RecordEvent()
    data object EntrySaved : RecordEvent()
    data class EntryMovedToRecycleBin(val entryId: Long) : RecordEvent()
}
