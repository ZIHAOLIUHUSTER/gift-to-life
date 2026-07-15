package com.gift.tolife.core.database

import com.gift.tolife.core.database.dao.EntryDao
import com.gift.tolife.core.database.dao.EntryTagDao
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.EntryTag
import com.gift.tolife.core.model.EntryType
import com.gift.tolife.core.common.ImageStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryRepository @Inject constructor(
    private val entryDao: EntryDao,
    private val entryTagDao: EntryTagDao,
    private val entryTransactions: EntryTransactions,
    private val imageStore: ImageStore
) {
    fun getAllEntries(): Flow<List<Entry>> = entryDao.getAllOrderByCreatedAtDesc()

    suspend fun getById(id: Long): Entry? = entryDao.getById(id)

    suspend fun save(entry: Entry): Long {
        val now = System.currentTimeMillis()
        return entryDao.insert(
            entry.copy(createdAt = now, updatedAt = now)
        )
    }

    suspend fun update(entry: Entry) {
        entryDao.update(entry.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(entry: Entry) {
        entryTagDao.deleteByEntryId(entry.id)
        entryDao.delete(entry)
    }

    suspend fun softDelete(entryId: Long): Boolean {
        return entryTransactions.softDelete(entryId)
    }

    suspend fun getDeletedEntries(): List<Entry> {
        return entryDao.getDeletedEntries()
    }

    suspend fun restoreEntry(id: Long): Boolean {
        return entryTransactions.restore(id)
    }

    suspend fun permanentlyDeleteAllDeleted() {
        val deleted = entryDao.getDeletedEntries()
        val imagePaths = deleted.mapNotNull { it.imagePath }
        entryDao.permanentlyDeleteAllDeleted()
        imageStore.deleteAll(imagePaths)
    }

    suspend fun getTags(entryId: Long): List<EntryTag> = entryTagDao.getByEntryId(entryId)


    suspend fun getAllTags(): List<EntryTag> {
        // Not directly available from DAO, but for now return empty
        // Will be expanded in phase 3 for filtering
        return emptyList()
    }

    suspend fun getRandomActiveEntry(): Entry? = entryDao.getRandomActiveEntry()

    fun observeSummaries(): Flow<List<Entry>> = entryDao.observeSummaries()
}
