package com.gift.tolife.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val imagePath: String? = null,
    val type: EntryType = EntryType.NORMAL,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val summaryStart: Long? = null,
    val summaryEnd: Long? = null,
    val imageDescription: String? = null,
    val isDeleted: Boolean = false,
    val summaryModel: String? = null
)
