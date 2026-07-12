package com.gift.tolife.core.database.dao

import androidx.room.*
import com.gift.tolife.core.model.EntryTag
import com.gift.tolife.core.model.TagType

@Dao
interface EntryTagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: EntryTag)

    @Query("SELECT * FROM entry_tags WHERE entryId = :entryId")
    suspend fun getByEntryId(entryId: Long): List<EntryTag>

    @Query("DELETE FROM entry_tags WHERE entryId = :entryId")
    suspend fun deleteByEntryId(entryId: Long)

    @Query("DELETE FROM entry_tags WHERE entryId = :entryId AND tag = :tag")
    suspend fun deleteByEntryIdAndTag(entryId: Long, tag: TagType)
}
