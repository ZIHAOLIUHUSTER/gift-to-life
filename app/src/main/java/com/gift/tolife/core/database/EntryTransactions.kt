package com.gift.tolife.core.database

import androidx.room.withTransaction
import com.gift.tolife.core.database.dao.EntryDao
import com.gift.tolife.core.database.dao.EntryTagDao
import com.gift.tolife.core.model.EntryTag
import com.gift.tolife.core.model.TagType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryTransactions @Inject constructor(
    private val database: AppDatabase,
    private val entryDao: EntryDao,
    private val entryTagDao: EntryTagDao
) {
    suspend fun replaceUserTags(entryId: Long, tags: Set<TagType>): Long {
        return database.withTransaction {
            check(entryDao.bumpEntryRevision(entryId, System.currentTimeMillis()) == 1) {
                "Entry is missing or deleted"
            }
            entryTagDao.deleteByEntryId(entryId)
            entryTagDao.insertAll(tags.map { EntryTag(entryId, it) })
            entryDao.getById(entryId)!!.entryRevision
        }
    }

    suspend fun softDelete(entryId: Long): Boolean = database.withTransaction {
        entryDao.softDeleteActive(entryId) == 1
    }

    suspend fun restore(entryId: Long): Boolean = database.withTransaction {
        entryDao.restoreDeleted(entryId) == 1
    }

    suspend fun updateUserContent(id: Long, content: String, imagePath: String?): Long {
        return database.withTransaction {
            val changed = entryDao.updateUserContent(
                id = id, content = content, imagePath = imagePath,
                updatedAt = System.currentTimeMillis()
            )
            check(changed == 1) { "Entry is missing or deleted" }
            entryDao.getById(id)!!.entryRevision
        }
    }

    suspend fun applyAiEnhancement(
        entryId: Long,
        expectedRevision: Long,
        imageDescription: String?,
        tags: Set<TagType>
    ): Boolean = database.withTransaction {
        val current = entryDao.getById(entryId) ?: return@withTransaction false
        if (current.isDeleted || current.entryRevision != expectedRevision) {
            return@withTransaction false
        }
        entryDao.updateImageDescription(id = entryId, expectedRevision = expectedRevision, description = imageDescription)
        entryTagDao.deleteByEntryId(entryId)
        entryTagDao.insertAll(tags.map { EntryTag(entryId, it) })
        true
    }
}
