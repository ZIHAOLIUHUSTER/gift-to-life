package com.gift.tolife.feature.record

import com.gift.tolife.core.model.EntryQuery
import com.gift.tolife.core.model.TagType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises production transition rules without Android, source inspection, or mocked UI. */
class RecordInputPolicyTest {
    @Test
    fun clearFiltersPreservesKeywordAndRemovesEveryFilter() {
        val query = EntryQuery(
            searchText = "雨天", startDate = 10L, endDate = 20L,
            hasImage = true, selectedTags = listOf(TagType.entries.first())
        )
        assertEquals(EntryQuery(searchText = "雨天"), RecordInputPolicy.clearFilters(query))
    }

    @Test
    fun clearingAlreadyEmptyFiltersIsIdempotent() {
        val query = EntryQuery(searchText = "仍在搜索")
        assertEquals(query, RecordInputPolicy.clearFilters(RecordInputPolicy.clearFilters(query)))
    }

    @Test
    fun latestVisibleSearchApplies() {
        assertTrue(RecordInputPolicy.applySearch(RecordInputPolicy.SearchInput("山", 2), 2, true, "山"))
    }

    @Test
    fun olderDebouncedTextCannotReplaceNewInput() {
        assertFalse(RecordInputPolicy.applySearch(RecordInputPolicy.SearchInput("山", 2), 2, true, "山雨"))
    }

    @Test
    fun closingSearchRejectsOutstandingDebounce() {
        assertFalse(RecordInputPolicy.applySearch(RecordInputPolicy.SearchInput("旧词", 2), 3, false, ""))
    }

    @Test
    fun closeThenReopenSameTextStillRejectsPreviousSession() {
        assertFalse(RecordInputPolicy.applySearch(RecordInputPolicy.SearchInput("同一个词", 2), 4, true, "同一个词"))
    }

    @Test
    fun emptySearchCanApplyWithoutRestoringPreviousKeyword() {
        assertTrue(RecordInputPolicy.applySearch(RecordInputPolicy.SearchInput("", 4), 4, true, ""))
    }

    @Test
    fun emptyWidgetFocusDoesNotConflictWithDraftImageOrQueuedShares() {
        assertEquals(
            RecordInputPolicy.ShareAction.FOCUS_ONLY,
            RecordInputPolicy.incomingShare(null, false, "未保存", true, true, true)
        )
    }

    @Test
    fun whitespaceOnlyShareIsFocusOnly() {
        assertEquals(
            RecordInputPolicy.ShareAction.FOCUS_ONLY,
            RecordInputPolicy.incomingShare(" \n", false, "", false, false, false)
        )
    }

    @Test
    fun emptyDraftReceivesTextOrImageDirectly() {
        assertEquals(
            RecordInputPolicy.ShareAction.RECEIVE,
            RecordInputPolicy.incomingShare("外部文字", false, " \n", false, false, false)
        )
        assertEquals(
            RecordInputPolicy.ShareAction.RECEIVE,
            RecordInputPolicy.incomingShare(null, true, "", false, false, false)
        )
    }

    @Test
    fun draftTextAndDraftImageEachRequireExplicitConflictResolution() {
        assertEquals(
            RecordInputPolicy.ShareAction.QUEUE,
            RecordInputPolicy.incomingShare("分享", false, "原稿", false, false, false)
        )
        assertEquals(
            RecordInputPolicy.ShareAction.QUEUE,
            RecordInputPolicy.incomingShare("分享", false, "", true, false, false)
        )
    }

    @Test
    fun savingOrEarlierQueuedSharePreventsSilentReceptionEvenWithEmptyDraft() {
        assertEquals(
            RecordInputPolicy.ShareAction.QUEUE,
            RecordInputPolicy.incomingShare("分享", false, "", false, true, false)
        )
        assertEquals(
            RecordInputPolicy.ShareAction.QUEUE,
            RecordInputPolicy.incomingShare("分享", false, "", false, false, true)
        )
    }

    @Test
    fun conflictResolutionIsFifoAndCannotRunWhileSaving() {
        assertTrue(RecordInputPolicy.canResolveShare(10, 10, false))
        assertFalse(RecordInputPolicy.canResolveShare(11, 10, false))
        assertFalse(RecordInputPolicy.canResolveShare(10, 10, true))
        assertFalse(RecordInputPolicy.canResolveShare(10, null, false))
    }

    @Test
    fun successfulUnchangedSaveClearsOriginalDraftAndReceivesRequestedHead() {
        assertEquals(
            RecordInputPolicy.SaveCompletion(clearDraft = true, receivePendingShare = true),
            RecordInputPolicy.completeSave(true, 8, 8, 10, 10)
        )
    }

    @Test
    fun failedSavePreservesBothOriginalDraftAndPendingShare() {
        assertEquals(
            RecordInputPolicy.SaveCompletion(clearDraft = false, receivePendingShare = false),
            RecordInputPolicy.completeSave(false, 8, 8, 10, 10)
        )
    }

    @Test
    fun textOrImageEditsDuringSavePreserveNewDraftAndRequireAnotherReception() {
        assertEquals(
            RecordInputPolicy.SaveCompletion(clearDraft = false, receivePendingShare = false),
            RecordInputPolicy.completeSave(true, 8, 9, 10, 10)
        )
    }

    @Test
    fun newerShareCannotReplaceOriginallyRequestedFifoHead() {
        val queue = listOf(10L, 11L)
        assertTrue(RecordInputPolicy.completeSave(true, 8, 8, 10, queue.first()).receivePendingShare)
        assertFalse(RecordInputPolicy.completeSave(true, 8, 8, 11, queue.first()).receivePendingShare)
        assertTrue(RecordInputPolicy.canResolveShare(11, queue.drop(1).first(), false))
    }

    @Test
    fun changedQueueHeadDoesNotReceiveWrongShareAfterSave() {
        assertEquals(
            RecordInputPolicy.SaveCompletion(clearDraft = true, receivePendingShare = false),
            RecordInputPolicy.completeSave(true, 8, 8, 10, 11)
        )
    }

    @Test
    fun ordinarySaveNeverAutoAcceptsIncomingShare() {
        assertEquals(
            RecordInputPolicy.SaveCompletion(clearDraft = true, receivePendingShare = false),
            RecordInputPolicy.completeSave(true, 8, 8, null, 10)
        )
    }
}
