package com.gift.tolife.core.database

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gift.tolife.core.database.query.EntryQuerySqlBuilder
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.EntryQuery
import com.gift.tolife.core.model.EntryTag
import com.gift.tolife.core.model.EntryType
import com.gift.tolife.core.model.TagType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 轻量搜索的真实 SQLite 行为；固定记录与 EntryQuerySqlBuilderTest 的导出 fixture 一致。
 * 只使用内存库，不接触用户数据、图片或模型服务。
 */
@RunWith(AndroidJUnit4::class)
class LightweightSearchTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
        FIXTURES.forEach { fixture ->
            database.entryDao().insert(
                Entry(
                    id = fixture.id,
                    content = fixture.content,
                    imagePath = fixture.imagePath,
                    type = fixture.type,
                    createdAt = fixture.createdAt,
                    updatedAt = fixture.createdAt,
                    isDeleted = fixture.deleted,
                )
            )
        }
        database.entryTagDao().insertAll(listOf(EntryTag(12, TagType.RESOURCE), EntryTag(13, TagType.EVENT)))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun twoWordsRequireEveryTermAndIgnoreWordOrder() = runBlocking {
        assertEquals(
            listOf(13L, 4L, 12L, 3L, 14L, 16L, 9L, 10L, 1L),
            pageAll(EntryQuery(searchText = "打印机 WiFi")),
        )
        assertEquals(
            listOf(16L, 13L, 4L, 12L, 3L, 14L, 9L, 10L, 1L),
            pageAll(EntryQuery(searchText = "WiFi 打印机")),
        )
        // 只含一个词的记录不得因排序好而混入结果。
        assertTrue(pageAll(EntryQuery(searchText = "打印机 路由器")).isEmpty())
    }

    @Test
    fun contiguousPhraseOutranksACloserScatteredPair() = runBlocking {
        // 16（打印机WiFi连着呢，跨度 7 且非整句）仍排在整句命中之后。
        assertEquals(
            listOf(13L, 4L, 12L, 3L, 14L),
            pageAll(EntryQuery(searchText = "打印机 WiFi")).take(5),
        )
    }

    @Test
    fun singleWordKeepsNewestFirstWithoutRanking() = runBlocking {
        assertEquals(
            listOf(13L, 4L, 7L, 10L, 1L, 12L, 9L, 3L, 14L, 16L),
            pageAll(EntryQuery(searchText = "打印机")),
        )
    }

    @Test
    fun asciiCaseIsFoldedAndDuplicateCaseVariantsStillPreferThePhrase() = runBlocking {
        assertEquals(
            listOf(13L, 2L, 4L, 10L, 1L, 12L, 9L, 3L, 11L, 14L, 16L),
            pageAll(EntryQuery(searchText = "wifi")),
        )
        assertEquals(
            listOf(11L, 13L, 2L, 4L, 10L, 1L, 12L, 9L, 3L, 14L, 16L),
            pageAll(EntryQuery(searchText = "WiFi wifi")),
        )
        assertEquals(listOf(8L), pageAll(EntryQuery(searchText = "android")))
        // Ä/ä 不在 SQLite 默认 LIKE 的 ASCII 折叠范围内。
        assertTrue(pageAll(EntryQuery(searchText = "ä")).isEmpty())
    }

    @Test
    fun likeMetacharactersAreMatchedLiterally() = runBlocking {
        assertEquals(listOf(7L), pageAll(EntryQuery(searchText = "_")))
        assertEquals(listOf(7L), pageAll(EntryQuery(searchText = "%_")))
        assertEquals(listOf(7L), pageAll(EntryQuery(searchText = "C:\\资料")))
        assertEquals(listOf(7L), pageAll(EntryQuery(searchText = "50%_C:\\资料")))
    }

    @Test
    fun filtersStillCombineWithMultiWordSearch() = runBlocking {
        assertEquals(listOf(4L), pageAll(EntryQuery(searchText = "打印机 WiFi", hasImage = true)))
        assertEquals(
            listOf(12L),
            pageAll(EntryQuery(searchText = "打印机 WiFi", selectedTags = listOf(TagType.RESOURCE))),
        )
        assertEquals(
            listOf(12L, 9L, 10L, 1L),
            pageAll(EntryQuery(searchText = "打印机 WiFi", startDate = 150L, endDate = 650L)),
        )
    }

    @Test
    fun deletedAndSummaryEntriesStayExcluded() = runBlocking {
        assertTrue(pageAll(EntryQuery(searchText = "已删除")).isEmpty())
        assertTrue(pageAll(EntryQuery(searchText = "总结")).isEmpty())
        // 资料记录仍参与普通搜索。
        assertTrue(12L in pageAll(EntryQuery(searchText = "打印机 WiFi")))
    }

    @Test
    fun oversizedQueryReturnsNoMatchesInsteadOfFailing() = runBlocking {
        assertTrue(pageAll(EntryQuery(searchText = "a".repeat(8193))).isEmpty())
    }

    @Test
    fun pagingAcrossPagesKeepsGlobalOrderWithoutDuplicates() = runBlocking {
        val source = database.entryDao().pagingSource(EntryQuerySqlBuilder.build(EntryQuery(searchText = "打印机 WiFi")))
        val pages = mutableListOf<List<Long>>()
        var key: Int? = null
        var first = true
        while (first || key != null) {
            val params = if (first) {
                PagingSource.LoadParams.Refresh<Int>(key = null, loadSize = 3, placeholdersEnabled = false)
            } else {
                PagingSource.LoadParams.Append<Int>(key = key!!, loadSize = 3, placeholdersEnabled = false)
            }
            first = false
            val result = source.load(params) as PagingSource.LoadResult.Page
            pages += result.data.map { it.id }
            key = result.nextKey
        }
        assertEquals(listOf(listOf(13L, 4L, 12L), listOf(3L, 14L, 16L), listOf(9L, 10L, 1L)), pages)
        assertEquals(pages.flatten(), pageAll(EntryQuery(searchText = "打印机 WiFi")))
    }

    private suspend fun pageAll(query: EntryQuery): List<Long> {
        val source = database.entryDao().pagingSource(EntryQuerySqlBuilder.build(query))
        val result = source.load(
            PagingSource.LoadParams.Refresh<Int>(key = null, loadSize = 60, placeholdersEnabled = false)
        )
        return when (result) {
            is PagingSource.LoadResult.Page -> result.data.map { it.id }
            is PagingSource.LoadResult.Error -> throw result.throwable
            else -> error("Unexpected paging result: $result")
        }
    }

    private data class Fixture(
        val id: Long,
        val content: String,
        val createdAt: Long,
        val imagePath: String? = null,
        val deleted: Boolean = false,
        val type: EntryType = EntryType.NORMAL,
    )

    companion object {
        private val FIXTURES = listOf(
            Fixture(1, "打印机连接方法\nWiFi 设置步骤", 500),
            Fixture(2, "WiFi 路由器说明", 900),
            Fixture(3, "打印机 WiFi 一起出现", 100),
            Fixture(4, "打印机 WiFi 耗材 下单", 800, imagePath = "a.webp"),
            Fixture(5, "已删除 打印机 WiFi", 950, deleted = true),
            Fixture(6, "总结 打印机 WiFi", 960, type = EntryType.SUMMARY),
            Fixture(7, "50%_C:\\资料 打印机", 700),
            Fixture(8, "ANDROID 和 android 都算", 300),
            Fixture(9, "打印机连接 WiFi 设置", 200),
            Fixture(10, "打印机 前面 WiFi 后面 相隔很远", 600),
            Fixture(11, "WiFi wifi 同一行", 50),
            Fixture(12, "资料 打印机 WiFi 说明", 400),
            Fixture(13, "打印机 WiFi", 1000),
            Fixture(14, "前言".repeat(200) + "打印机 WiFi 步骤", 20),
            Fixture(16, "打印机WiFi连着呢", 5),
            Fixture(17, "Ä 慕尼黑", 450),
        )
    }
}
