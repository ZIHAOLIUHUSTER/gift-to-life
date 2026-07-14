package com.gift.tolife.core.database.model

import com.gift.tolife.core.model.EntryType
import com.gift.tolife.core.model.TagType

data class EntryListRow(
    val id: Long,
    val content: String,
    val imagePath: String?,
    val type: EntryType,
    val createdAt: Long,
    val updatedAt: Long,
    val imageDescription: String?,
    val tagsCsv: String?
) {
    fun tags(): List<TagType> = tagsCsv
        ?.split(',')
        ?.mapNotNull { value -> TagType.entries.find { it.name == value } }
        .orEmpty()
}
