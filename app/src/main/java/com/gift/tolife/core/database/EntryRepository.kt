package com.gift.tolife.core.database

import com.gift.tolife.core.database.dao.EntryDao
import com.gift.tolife.core.database.dao.EntryTagDao
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.EntryTag
import com.gift.tolife.core.model.TagType
import com.gift.tolife.core.common.ImageStore
import kotlinx.coroutines.flow.Flow
import kotlin.random.Random
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

    fun observeSummaries(): Flow<List<Entry>> = entryDao.observeSummaries()

    /** 随机取一条活跃记录（含标签），随机回顾页与桌面小组件共用 */
    suspend fun getRandomEntryWithTags(): Pair<Entry, List<TagType>>? {
        val ids = entryDao.getAllActiveIds()
        if (ids.isEmpty()) return null
        val entry = entryDao.getById(ids[Random.nextInt(ids.size)]) ?: return null
        return entry to getTags(entry.id).map { it.tag }
    }
}
