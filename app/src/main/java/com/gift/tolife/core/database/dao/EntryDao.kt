package com.gift.tolife.core.database.dao

import androidx.room.*
import com.gift.tolife.core.model.Entry
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

    @Query("SELECT * FROM entries ORDER BY createdAt DESC")
    fun getAllOrderByCreatedAtDesc(): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE type = :type ORDER BY createdAt DESC")
    fun getByType(type: String): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE content LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchByContent(query: String): Flow<List<Entry>>
}
