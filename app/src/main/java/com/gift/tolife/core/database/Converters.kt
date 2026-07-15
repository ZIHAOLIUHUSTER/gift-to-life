package com.gift.tolife.core.database

import androidx.room.TypeConverter
import com.gift.tolife.core.model.EntryType
import com.gift.tolife.core.model.TagType

/**
 * Room type converters for enum types.
 * WARNING: Enum values are stored by name(). DO NOT rename enum constants
 * without a database migration — existing data will become unreadable.
 */
class Converters {
    @TypeConverter
    fun fromEntryType(value: EntryType): String = value.name

    @TypeConverter
    fun toEntryType(value: String): EntryType =
        EntryType.entries.find { it.name == value } ?: EntryType.NORMAL

    @TypeConverter
    fun fromTagType(value: TagType): String = value.name

    @TypeConverter
    fun toTagType(value: String): TagType =
        TagType.entries.find { it.name == value } ?: TagType.entries.first()
}
