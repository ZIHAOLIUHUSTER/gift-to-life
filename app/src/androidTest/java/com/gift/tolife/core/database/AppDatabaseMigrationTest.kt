package com.gift.tolife.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.coroutines.runBlocking
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
    fun migrate1To4() = runBlocking {
        // Create v1 database (without imageDescription, isDeleted, summaryModel, entryRevision)
        createHistoricalDatabase(TEST_DB, 1).apply {
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
            execSQL("""
                INSERT INTO entries VALUES (2, 'test with image', '/data/images/a.webp', 'NORMAL', 1700000001000, 1700000001000, NULL, NULL)
            """)
            close()
        }

        // Migrate
        helper.runMigrationsAndValidate(TEST_DB, 4, true, Migrations.MIGRATION_1_3, Migrations.MIGRATION_3_4)

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

        // Verify entry with image path preserved
        val entry2 = db.entryDao().getById(2)
        assertEquals("/data/images/a.webp", entry2?.imagePath)
        assertEquals(0L, entry2?.entryRevision ?: -1)

        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate1To4_withTags() = runBlocking {
        // Create v1 database WITH entry_tags table (some historical v1 had this)
        createHistoricalDatabase(TEST_DB_TAGS, 1).apply {
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
                CREATE TABLE entry_tags (
                    entryId INTEGER NOT NULL,
                    tag TEXT NOT NULL,
                    PRIMARY KEY(entryId, tag),
                    FOREIGN KEY(entryId) REFERENCES entries(id) ON DELETE CASCADE
                )
            """)
            execSQL("INSERT INTO entries VALUES (1, 'tagged entry', NULL, 'NORMAL', 1700000000000, 1700000000000, NULL, NULL)")
            execSQL("INSERT INTO entry_tags VALUES (1, 'FLASH_THOUGHT')")
            execSQL("INSERT INTO entry_tags VALUES (1, 'EMOTION')")
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB_TAGS, 4, true, Migrations.MIGRATION_1_3, Migrations.MIGRATION_3_4)

        val db = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java, TEST_DB_TAGS
        ).addMigrations(Migrations.MIGRATION_1_3, Migrations.MIGRATION_3_4).build()

        val entry = db.entryDao().getById(1)
        assertEquals("tagged entry", entry?.content)
        assertEquals(0L, entry?.entryRevision)
        assertFalse(entry?.isDeleted ?: true)

        val tags = db.entryTagDao().getByEntryId(1).map { it.tag }.toSet()
        assertEquals(setOf(TagType.FLASH_THOUGHT, TagType.EMOTION), tags)

        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate3To4() = runBlocking {
        createHistoricalDatabase(TEST_DB, 3).apply {
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

        helper.runMigrationsAndValidate(TEST_DB, 4, true, Migrations.MIGRATION_3_4)

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

    // 仓库仅保留 v4 schema；旧结构使用独立测试库的 SQL fixture 创建。
    private fun createHistoricalDatabase(name: String, version: Int): SupportSQLiteDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(name)
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        error("历史测试库不应自行升级")
                    }
                })
                .build()
        ).writableDatabase
    }

    companion object {
        private const val TEST_DB = "migration_test"
        private const val TEST_DB_TAGS = "migration_test_tags"
    }
}
