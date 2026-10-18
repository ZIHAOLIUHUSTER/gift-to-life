package com.gift.tolife.feature.record

import com.gift.tolife.core.model.EntryQuery

/** Pure rules shared by the debounce and external-share transitions. */
object RecordInputPolicy {
    data class SearchInput(val text: String, val generation: Long)

    fun clearFilters(query: EntryQuery): EntryQuery = query.copy(
        selectedTags = emptyList(), startDate = null, endDate = null, hasImage = null
    )

    fun applySearch(input: SearchInput, generation: Long, isSearchMode: Boolean, visibleText: String): Boolean =
        isSearchMode && input.generation == generation && input.text == visibleText

    enum class ShareAction { FOCUS_ONLY, RECEIVE, QUEUE }

    fun incomingShare(
        sharedText: String?, sharedHasImage: Boolean,
        draftText: String, draftHasImage: Boolean, isSaving: Boolean, hasPendingShare: Boolean
    ): ShareAction = when {
        sharedText.isNullOrBlank() && !sharedHasImage -> ShareAction.FOCUS_ONLY
        receiveImmediately(draftText, draftHasImage, isSaving, hasPendingShare) -> ShareAction.RECEIVE
        else -> ShareAction.QUEUE
    }

    fun canResolveShare(requestedId: Long, firstPendingId: Long?, isSaving: Boolean): Boolean =
        !isSaving && requestedId == firstPendingId

    fun receiveImmediately(text: String, hasImage: Boolean, isSaving: Boolean, hasPendingShare: Boolean): Boolean =
        text.isBlank() && !hasImage && !isSaving && !hasPendingShare

    data class SaveCompletion(val clearDraft: Boolean, val receivePendingShare: Boolean)

    fun completeSave(
        succeeded: Boolean, savedVersion: Long, currentVersion: Long,
        requestedId: Long?, firstPendingId: Long?
    ): SaveCompletion = SaveCompletion(
        clearDraft = succeeded && savedVersion == currentVersion,
        receivePendingShare = succeeded && receiveAfterSave(savedVersion, currentVersion, requestedId, firstPendingId)
    )

    /** A successful save cannot replace edits made during I/O, or a different queued share. */
    fun receiveAfterSave(savedVersion: Long, currentVersion: Long, requestedId: Long?, firstPendingId: Long?): Boolean =
        savedVersion == currentVersion && requestedId != null && requestedId == firstPendingId
}
