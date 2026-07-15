package com.gift.tolife.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.withTransaction
import com.gift.tolife.core.database.dao.EntryDao
import com.gift.tolife.core.database.dao.EntryTagDao
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.EntryTag

@Database(
    entities = [Entry::class, EntryTag::class],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun entryTagDao(): EntryTagDao

    suspend fun replaceAll(stagedEntries: List<StagedImportEntry>): Int = withTransaction {
        entryTagDao().deleteAll()
        entryDao().deleteAll()
        var count = 0
        stagedEntries.forEach { staged ->
            val id = entryDao().insert(staged.entry)
            if (staged.tags.isNotEmpty()) {
                entryTagDao().insertAll(staged.tags.map { EntryTag(id, it) })
            }
            count++
        }
        count
    }
}
