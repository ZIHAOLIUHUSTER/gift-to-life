package com.gift.tolife.core.database

import com.gift.tolife.core.database.dao.EntryDao
import com.gift.tolife.core.database.dao.EntryTagDao
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.EntryQuery
import com.gift.tolife.core.model.EntryTag
import com.gift.tolife.core.model.EntryType
import com.gift.tolife.core.model.TagType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryRepository @Inject constructor(
    private val entryDao: EntryDao,
    private val entryTagDao: EntryTagDao
) {
    fun getAllEntries(): Flow<List<Entry>> = entryDao.getAllOrderByCreatedAtDesc()

    fun getFilteredEntries(query: EntryQuery): Flow<List<Entry>> {
        val baseFlow = if (query.searchText.isNotBlank()) {
            entryDao.searchByContent(query.searchText)
        } else {
            entryDao.getAllOrderByCreatedAtDesc()
        }
        return baseFlow.map { entries ->
            filterEntries(entries, query)
        }
    }

    private suspend fun filterEntries(entries: List<Entry>, query: EntryQuery): List<Entry> {
        var result = entries

        // 时间范围
        query.startDate?.let { start ->
            result = result.filter { it.createdAt >= start }
        }
        query.endDate?.let { end ->
            result = result.filter { it.createdAt <= end }
        }

        // 有无图片
        query.hasImage?.let { hasImage ->
            result = result.filter {
                if (hasImage) !it.imagePath.isNullOrBlank()
                else it.imagePath.isNullOrBlank()
            }
        }

        // 标签筛选
        if (query.selectedTags.isNotEmpty()) {
            val entryIds = entryTagDao.getEntryIdsByTags(query.selectedTags)
            result = result.filter { it.id in entryIds }
        }

        return result
    }

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

    suspend fun getTags(entryId: Long): List<EntryTag> = entryTagDao.getByEntryId(entryId)

    suspend fun setTags(entryId: Long, tags: List<TagType>) {
        entryTagDao.deleteByEntryId(entryId)
        tags.forEach { tag ->
            entryTagDao.insert(EntryTag(entryId = entryId, tag = tag))
        }
    }

    suspend fun getAllTags(): List<EntryTag> {
        // Not directly available from DAO, but for now return empty
        // Will be expanded in phase 3 for filtering
        return emptyList()
    }
}
