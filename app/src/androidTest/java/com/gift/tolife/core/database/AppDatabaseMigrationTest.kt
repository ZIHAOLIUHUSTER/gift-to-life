package com.gift.tolife.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gift.tolife.core.database.dao.EntryDao
import com.gift.tolife.core.database.dao.EntryTagDao
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.EntryTag
import com.gift.tolife.core.model.EntryType
import com.gift.tolife.core.model.TagType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To4() {
        // Create v1 database (without imageDescription)
        val db1 = helper.createDatabase(TEST_DB, 1).apply {
            execSQL("""
                CREATE TABLE entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    content TEXT NOT NULL,
                    imagePath TEXT,
                    type TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    summaryStart INTEGER,
                    summaryEnd INTEGER
                )
            """)
            execSQL("""
                INSERT INTO entries VALUES (1, 'test', NULL, 'NORMAL', 1700000000000, 1700000000000, NULL, NULL)
            """)
            close()
        }

        // Migrate
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 4, true, Migrations.MIGRATION_1_3, Migrations.MIGRATION_3_4)

        // Verify
        val db = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java, TEST_DB
        ).addMigrations(Migrations.MIGRATION_1_3, Migrations.MIGRATION_3_4).build()

        val entry = db.entryDao().getById(1)
        assertEquals("test", entry?.content)
        assertEquals(1700000000000L, entry?.createdAt)
        assertFalse(entry?.isDeleted ?: true)
        assertEquals(0L, entry?.entryRevision ?: -1)
        assertEquals(null, entry?.imageDescription)
        assertEquals(null, entry?.summaryModel)

        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate3To4() {
        val db3 = helper.createDatabase(TEST_DB, 3).apply {
            execSQL("""
                CREATE TABLE entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    content TEXT NOT NULL,
                    imagePath TEXT,
                    type TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    summaryStart INTEGER,
                    summaryEnd INTEGER,
                    imageDescription TEXT,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    summaryModel TEXT
                )
            """)
            execSQL("""
                CREATE TABLE entry_tags (
                    entryId INTEGER NOT NULL,
                    tag TEXT NOT NULL,
                    PRIMARY KEY(entryId, tag),
                    FOREIGN KEY(entryId) REFERENCES entries(id) ON DELETE CASCADE
                )
            """)
            execSQL("INSERT INTO entries VALUES (1, 'test v3', NULL, 'NORMAL', 1700000000000, 1700000000000, NULL, NULL, 'a picture', 0, 'deepseek-chat')")
            execSQL("INSERT INTO entry_tags VALUES (1, 'FLASH_THOUGHT')")
            execSQL("INSERT INTO entry_tags VALUES (1, 'KNOWLEDGE')")
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 4, true, Migrations.MIGRATION_3_4)

        val db = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java, TEST_DB
        ).addMigrations(Migrations.MIGRATION_3_4).build()

        val entry = db.entryDao().getById(1)
        assertEquals("test v3", entry?.content)
        assertEquals("a picture", entry?.imageDescription)
        assertEquals("deepseek-chat", entry?.summaryModel)
        assertEquals(0L, entry?.entryRevision)

        val tags = db.entryTagDao().getByEntryId(1).map { it.tag }.toSet()
        assertEquals(setOf(TagType.FLASH_THOUGHT, TagType.KNOWLEDGE), tags)

        db.close()
    }

    companion object {
        private const val TEST_DB = "migration_test"
    }
}
