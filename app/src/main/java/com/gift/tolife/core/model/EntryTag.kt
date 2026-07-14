package com.gift.tolife.core.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "entry_tags",
    primaryKeys = ["entryId", "tag"],
    foreignKeys = [
        ForeignKey(
            entity = Entry::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("entryId"), Index(value = ["tag", "entryId"], name = "index_entry_tags_tag_entryId")]
)
data class EntryTag(
    val entryId: Long,
    val tag: TagType
)
