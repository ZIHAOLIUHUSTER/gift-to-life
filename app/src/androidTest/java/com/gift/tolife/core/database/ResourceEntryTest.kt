package com.gift.tolife.core.database

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gift.tolife.core.database.query.EntryQuerySqlBuilder
import com.gift.tolife.core.common.ImageStore
import com.gift.tolife.feature.record.EntryInputParser
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.EntryQuery
import com.gift.tolife.core.model.EntryTag
import com.gift.tolife.core.model.EntryType
import com.gift.tolife.core.model.TagType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 独立内存库，不接触用户记录、图片文件或模型服务。 */
@RunWith(AndroidJUnit4::class)
class ResourceEntryTest {
    private lateinit var database: AppDatabase
    private lateinit var transactions: EntryTransactions
    private lateinit var repository: EntryRepository

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
        transactions = EntryTransactions(database, database.entryDao(), database.entryTagDao())
        repository = EntryRepository(
            database.entryDao(), database.entryTagDao(), transactions,
            ImageStore(InstrumentationRegistry.getInstrumentation().targetContext)
        )
        insert(1, "日记")
        insert(2, "值得记住的文摘", tags = listOf(TagType.EXCERPT))
        insert(3, "打印机连接方法", image = "resource.webp", tags = listOf(TagType.RESOURCE))
        insert(4, "报销流程", image = "mixed.webp", tags = listOf(TagType.EVENT, TagType.EXCERPT, TagType.RESOURCE))
        insert(5, "已删除的记录", deleted = true)
        insert(6, "历史总结", type = EntryType.SUMMARY)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun summariesAndAllMemoryCandidatesExcludeResourcesButKeepExcerpts() = runBlocking {
        val dao = database.entryDao()
        assertEquals(listOf(1L, 2L), dao.getNormalEntriesInRange(TIME, TIME).map { it.id })
        assertEquals(2, dao.getNormalEntryCountInRange(TIME, TIME))
        assertEquals(listOf(1L, 2L), dao.getAllActiveIds())
        val monthDay = Instant.ofEpochMilli(TIME).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MM-dd"))
        assertEquals(listOf(1L, 2L), dao.getEntriesByMonthDay(monthDay).map { it.id })
    }

    @Test
    fun resourcesRemainVisibleInSearchStatisticsAndExport() = runBlocking {
        assertEquals(listOf(4L, 3L, 2L, 1L), page(EntryQuery()))
        assertEquals(listOf(3L), page(EntryQuery(searchText = "打印机", selectedTags = listOf(TagType.RESOURCE))))
        assertEquals(listOf(4L, 3L), page(EntryQuery(selectedTags = listOf(TagType.RESOURCE), hasImage = true)))
        val dao = database.entryDao()
        assertEquals(4, dao.getTotalEntryCount())
        assertEquals(4, dao.getMonthlyCount(TIME, TIME))
        assertEquals(2, dao.getImageCount())
        assertEquals((1L..6L).toList(), dao.getEntriesForExportAfterId(0L, 30).map { it.id })
    }

    @Test
    fun manuallyAddingAndRemovingResourceImmediatelyChangesEligibility() = runBlocking {
        transactions.replaceUserTags(1L, setOf(TagType.RESOURCE))
        assertEquals(listOf(2L), database.entryDao().getAllActiveIds())
        assertEquals(1, database.entryDao().getNormalEntryCountInRange(TIME, TIME))
        transactions.replaceUserTags(1L, setOf(TagType.EVENT))
        assertEquals(listOf(1L, 2L), database.entryDao().getAllActiveIds())
        assertEquals(2, database.entryDao().getNormalEntryCountInRange(TIME, TIME))
    }

    @Test
    fun manualResourcesAndExcerptsRejectAiEnhancement() = runBlocking {
        assertFalse(transactions.applyAiEnhancement(4L, 0L, "报销示意图", setOf(TagType.KNOWLEDGE)))
        assertFalse(transactions.applyAiEnhancement(2L, 0L, null, setOf(TagType.KNOWLEDGE)))
        assertEquals(
            setOf(TagType.RESOURCE, TagType.EXCERPT, TagType.EVENT),
            database.entryTagDao().getByEntryId(4L).map { it.tag }.toSet()
        )
        assertEquals(listOf(TagType.EXCERPT), database.entryTagDao().getByEntryId(2L).map { it.tag })
        assertEquals(listOf(1L, 2L), database.entryDao().getAllActiveIds())
        assertEquals("mixed.webp", database.entryDao().getById(4L)?.imagePath)
        assertNull(database.entryDao().getById(4L)?.imageDescription)
    }

    @Test
    fun staleAiResultsCannotUndoManualResourceSelection() = runBlocking {
        val revision = transactions.replaceUserTags(1L, setOf(TagType.RESOURCE))
        assertEquals(1L, revision)
        assertFalse(transactions.applyAiEnhancement(1L, 0L, null, setOf(TagType.EVENT)))
        assertEquals(listOf(TagType.RESOURCE), database.entryTagDao().getByEntryId(1L).map { it.tag })
    }

    @Test
    fun databaseRestoreKeepsResourceTagsAndExclusion() = runBlocking {
        database.replaceAll(listOf(StagedImportEntry(
            Entry(id = 10, content = "恢复的资料", createdAt = TIME),
            listOf(TagType.RESOURCE)
        )))
        assertEquals(listOf(TagType.RESOURCE), database.entryTagDao().getByEntryId(10L).map { it.tag })
        assertTrue(database.entryDao().getAllActiveIds().isEmpty())
        assertTrue(database.entryDao().getNormalEntriesInRange(TIME, TIME).isEmpty())
        assertEquals(listOf(10L), page(EntryQuery(selectedTags = listOf(TagType.RESOURCE))))
    }

    @Test
    fun prefixedDraftSavesContentAndManualTagInTheInitialRevision() = runBlocking {
        val parsed = EntryInputParser.parse("#2打印机步骤")
        val id = repository.save(Entry(content = parsed.content, imagePath = "fixture.webp"), parsed.tags)
        val saved = database.entryDao().getById(id)!!
        assertEquals("打印机步骤", saved.content)
        assertEquals("fixture.webp", saved.imagePath)
        assertEquals(0L, saved.entryRevision)
        assertEquals(listOf(TagType.RESOURCE), repository.getTags(id).map { it.tag })
        assertFalse(id in database.entryDao().getAllActiveIds())
        assertEquals(listOf(id), page(EntryQuery(searchText = "打印机步骤", selectedTags = listOf(TagType.RESOURCE))))
    }

    @Test
    fun prefixOnlyImageExcerptSavesWithoutTheCommandText() = runBlocking {
        val parsed = EntryInputParser.parse("#3")
        val id = repository.save(Entry(content = parsed.content, imagePath = "fixture.webp"), parsed.tags)
        assertEquals("", database.entryDao().getById(id)?.content)
        assertEquals(listOf(TagType.EXCERPT), repository.getTags(id).map { it.tag })
        assertFalse(parsed.runAi)
    }

    @Test
    fun failedTagInsertionRollsBackTheEntireNewRecord() = runBlocking {
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_resource_tag BEFORE INSERT ON entry_tags
            WHEN NEW.tag = 'RESOURCE'
            BEGIN SELECT RAISE(ABORT, 'fixture tag insertion failure'); END
        """)
        val result = runCatching {
            repository.save(Entry(content = "不能留下未分类的记录"), setOf(TagType.RESOURCE))
        }
        assertTrue(result.isFailure)
        assertEquals(4, database.entryDao().getTotalEntryCount())
        assertEquals((1L..6L).toList(), database.entryDao().getEntriesForExportAfterId(0L, 30).map { it.id })
        assertEquals(emptyList<Long>(), page(EntryQuery(searchText = "不能留下")))
    }

    @Test
    fun ordinaryNewRecordsStillAllowAiEnhancement() = runBlocking {
        val parsed = EntryInputParser.parse("#1一个想法")
        val id = repository.save(Entry(content = parsed.content), parsed.tags)
        assertTrue(parsed.runAi)
        assertTrue(transactions.applyAiEnhancement(id, 0L, null, setOf(TagType.FLASH_THOUGHT)))
        assertEquals(listOf(TagType.FLASH_THOUGHT), repository.getTags(id).map { it.tag })
    }

    private suspend fun insert(
        id: Long,
        content: String,
        image: String? = null,
        tags: List<TagType> = emptyList(),
        deleted: Boolean = false,
        type: EntryType = EntryType.NORMAL
    ) {
        database.entryDao().insert(Entry(
            id = id, content = content, imagePath = image, type = type,
            createdAt = TIME, updatedAt = TIME, isDeleted = deleted
        ))
        database.entryTagDao().insertAll(tags.map { EntryTag(id, it) })
    }

    private suspend fun page(query: EntryQuery): List<Long> {
        val source = database.entryDao().pagingSource(EntryQuerySqlBuilder.build(query))
        val result = source.load(PagingSource.LoadParams.Refresh<Int>(
            key = null, loadSize = 30, placeholdersEnabled = false
        ))
        return when (result) {
            is PagingSource.LoadResult.Page -> result.data.map { it.id }
            is PagingSource.LoadResult.Error -> throw result.throwable
            else -> error("Unexpected paging result: $result")
        }
    }

    companion object {
        private const val TIME = 1_700_000_000_000L
    }
}
