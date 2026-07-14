package com.gift.tolife.core.database.dao

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.gift.tolife.core.database.model.EntryListRow
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.EntryTag
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Insert
    suspend fun insert(entry: Entry): Long

    @Update
    suspend fun update(entry: Entry)

    @Delete
    suspend fun delete(entry: Entry)

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: Long): Entry?

    @Query("SELECT * FROM entries WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllOrderByCreatedAtDesc(): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE type = 'NORMAL' AND isDeleted = 0 AND createdAt BETWEEN :start AND :end ORDER BY createdAt ASC")
    suspend fun getNormalEntriesInRange(start: Long, end: Long): List<Entry>

    @Query("SELECT * FROM entries WHERE type = :type ORDER BY createdAt DESC")
    fun getByType(type: String): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE content LIKE '%' || :query || '%' AND isDeleted = 0 ORDER BY createdAt DESC")
    fun searchByContent(query: String): Flow<List<Entry>>

    @Query("DELETE FROM entries")
    suspend fun deleteAll()

    @Query("SELECT * FROM entries WHERE isDeleted = 1 ORDER BY createdAt DESC")
    suspend fun getDeletedEntries(): List<Entry>

    @Query("UPDATE entries SET isDeleted = 1 WHERE id = :id")
    suspend fun softDelete(id: Long)

    @Query("UPDATE entries SET isDeleted = 0 WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("DELETE FROM entries WHERE isDeleted = 1")
    suspend fun permanentlyDeleteAllDeleted()

    @Query("""
        UPDATE entries
        SET content = :content,
            imagePath = :imagePath,
            imageDescription = NULL,
            updatedAt = :updatedAt,
            entryRevision = entryRevision + 1
        WHERE id = :id AND isDeleted = 0
    """)
    suspend fun updateUserContent(id: Long, content: String, imagePath: String?, updatedAt: Long): Int

    @Query("UPDATE entries SET isDeleted = 1 WHERE id = :id AND isDeleted = 0")
    suspend fun softDeleteActive(id: Long): Int

    @Query("UPDATE entries SET isDeleted = 0 WHERE id = :id AND isDeleted = 1")
    suspend fun restoreDeleted(id: Long): Int

    @Query("DELETE FROM entries WHERE id = :id AND isDeleted = 1")
    suspend fun permanentlyDeleteOne(id: Long): Int

    @Query("""
        UPDATE entries
        SET entryRevision = entryRevision + 1,
            updatedAt = :updatedAt
        WHERE id = :id AND isDeleted = 0
    """)
    suspend fun bumpEntryRevision(id: Long, updatedAt: Long): Int

    @Query("""
        UPDATE entries
        SET imageDescription = :description
        WHERE id = :id AND entryRevision = :expectedRevision AND isDeleted = 0
    """)
    suspend fun updateImageDescription(id: Long, expectedRevision: Long, description: String?): Int

    @Query("SELECT * FROM entries ORDER BY createdAt DESC")
    suspend fun getAllEntriesAsList(): List<Entry>

    @Query("SELECT * FROM entries WHERE type = 'NORMAL' AND isDeleted = 0 ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomActiveEntry(): Entry?

    @Query("SELECT * FROM entries WHERE type = 'SUMMARY' AND isDeleted = 0 ORDER BY summaryStart DESC, createdAt DESC")
    fun observeSummaries(): Flow<List<Entry>>

    @RawQuery(observedEntities = [Entry::class, EntryTag::class])
    fun pagingSource(query: SupportSQLiteQuery): PagingSource<Int, EntryListRow>
}
