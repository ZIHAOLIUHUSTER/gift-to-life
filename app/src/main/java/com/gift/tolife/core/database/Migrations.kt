package com.gift.tolife.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    val MIGRATION_1_3 = object : Migration(1, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "entries", "imageDescription", "TEXT")
            addColumnIfMissing(db, "entries", "isDeleted", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "entries", "summaryModel", "TEXT")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE entries ADD COLUMN entryRevision INTEGER NOT NULL DEFAULT 0")
            createIndexes(db)
        }
    }

    private fun addColumnIfMissing(db: SupportSQLiteDatabase, table: String, column: String, declaration: String) {
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) return
            }
        }
        db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $declaration")
    }

    private fun createIndexes(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_entries_deleted_created_id ON entries(isDeleted, createdAt, id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_entries_type_created_id ON entries(type, createdAt, id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_entry_tags_tag_entryId ON entry_tags(tag, entryId)")
    }
}
