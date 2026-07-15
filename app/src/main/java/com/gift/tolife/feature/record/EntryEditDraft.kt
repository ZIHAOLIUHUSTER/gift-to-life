package com.gift.tolife.feature.record

import android.net.Uri
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.TagType

sealed interface ImageChange {
    data object Keep : ImageChange
    data object Remove : ImageChange
    data class Replace(val uri: Uri) : ImageChange
}

data class EntryEditDraft(
    val entryId: Long,
    val expectedRevision: Long,
    val content: String,
    val tags: Set<TagType>,
    val originalImagePath: String?,
    val imageChange: ImageChange = ImageChange.Keep
) {
    companion object {
        fun from(entry: Entry, tags: Set<TagType> = emptySet()) = EntryEditDraft(
            entryId = entry.id,
            expectedRevision = entry.entryRevision,
            content = entry.content,
            tags = tags,
            originalImagePath = entry.imagePath
        )
    }

    fun isDirtyComparedWith(entry: Entry, originalTags: Set<TagType>): Boolean {
        return content != entry.content ||
            tags != originalTags ||
            imageChange != ImageChange.Keep
    }
}