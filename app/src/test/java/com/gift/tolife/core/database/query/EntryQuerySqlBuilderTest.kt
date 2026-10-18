package com.gift.tolife.core.database.query

import androidx.sqlite.db.SupportSQLiteProgram
import com.gift.tolife.core.model.EntryQuery
import com.gift.tolife.core.model.TagType
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 只验证 SQL 结构与绑定参数顺序（JVM 内没有真实 SQLite）。
 *
 * 排序语义由 `.pi/lab/check-search-sql.py` 用 Python 自带 SQLite 执行
 * `build/test-fixtures/lightweight-search.json` 交叉验证；Android Room 用例见
 * `androidTest/.../LightweightSearchTest.kt`。
 */
class EntryQuerySqlBuilderTest {

    private class Captured(val sql: String, val args: List<Any?>)

    private class RecordingProgram : SupportSQLiteProgram {
        val indexes = mutableListOf<Int>()
        val args = mutableListOf<Any?>()

        override fun bindNull(index: Int) = record(index, null)
        override fun bindLong(index: Int, value: Long) = record(index, value)
        override fun bindDouble(index: Int, value: Double) = record(index, value)
        override fun bindString(index: Int, value: String) = record(index, value)
        override fun bindBlob(index: Int, value: ByteArray) = record(index, value)

        private fun record(index: Int, value: Any?) {
            indexes += index
            args += value
        }

        override fun clearBindings() = Unit
        override fun close() = Unit
    }

    private fun capture(query: EntryQuery): Captured {
        val built = EntryQuerySqlBuilder.build(query)
        val program = RecordingProgram()
        built.bindTo(program)
        // SimpleSQLiteQuery 按数组顺序 1..n 绑定；顺序错了 SQL 参数就会错位。
        assertEquals((1..built.argCount).toList(), program.indexes)
        return Captured(built.sql, program.args)
    }

    @Test
    fun `compound filters bind terms dates tags then ranking args in placeholder order`() {
        val captured = capture(
            EntryQuery(
                searchText = "打印机 WiFi",
                startDate = 150L,
                endDate = 650L,
                hasImage = true,
                selectedTags = listOf(TagType.RESOURCE, TagType.EVENT),
            )
        )
        assertEquals(
            listOf(
                "%打印机%", "%WiFi%", 150L, 650L, "RESOURCE", "EVENT",
                "%打印机 WiFi%",
                "打印机", "wifi",
                "打印机", "wifi",
            ),
            captured.args,
        )
        assertEquals(3, Regex("LIKE \\? ESCAPE").findAll(captured.sql).count())
        assertEquals(4, Regex("instr\\(lower\\(e\\.content\\), \\?\\)").findAll(captured.sql).count())
        assertTrue(captured.sql.contains("e.imagePath IS NOT NULL AND e.imagePath != ''"))
        assertTrue(captured.sql.contains("ORDER BY CASE WHEN e.content LIKE ? ESCAPE"))
    }

    @Test
    fun `single word search keeps the plain newest first order`() {
        val captured = capture(EntryQuery(searchText = "打印机"))
        assertEquals(listOf<Any?>("%打印机%"), captured.args)
        assertFalse(captured.sql.contains("CASE WHEN"))
        assertFalse(captured.sql.contains("instr(lower"))
        assertTrue(captured.sql.contains("ORDER BY e.createdAt DESC, e.id DESC"))
    }

    @Test
    fun `duplicate case variant terms still reward the original phrase`() {
        val captured = capture(EntryQuery(searchText = "WiFi wifi"))
        // 去重后只剩 WiFi，但原查询是两段；整句 CASE 保留，接近度表达式不需要。
        assertEquals(listOf<Any?>("%WiFi%", "%WiFi wifi%"), captured.args)
        assertTrue(captured.sql.contains("CASE WHEN e.content LIKE ? ESCAPE"))
        assertFalse(captured.sql.contains("instr(lower"))
    }

    @Test
    fun `overflowing query becomes an impossible condition without any bind`() {
        val captured = capture(EntryQuery(searchText = "a".repeat(8193)))
        assertEquals(emptyList<Any?>(), captured.args)
        assertTrue(captured.sql.contains(" AND 0"))
        assertFalse(captured.sql.contains("LIKE ?"))
        assertTrue(captured.sql.contains("ORDER BY e.createdAt DESC, e.id DESC"))
    }

    /**
     * 导出真实 builder 产物供离线 SQLite 执行；页面期望见 `expectedIds`。
     * 与 `LightweightSearchTest` 的 fixture 必须保持一致。
     */
    @Test
    fun `export sqlite fixture for offline ordering verification`() {
        val cases: List<Pair<String, Triple<EntryQuery, String, List<Long>>>> = listOf(
            "two words order A" to Triple(EntryQuery(searchText = "打印机 WiFi"), "打印机 WiFi", listOf(13, 4, 12, 3, 14, 16, 9, 10, 1)),
            "two words reverse order" to Triple(EntryQuery(searchText = "WiFi 打印机"), "WiFi 打印机", listOf(16, 13, 4, 12, 3, 14, 9, 10, 1)),
            "single word newest first" to Triple(EntryQuery(searchText = "打印机"), "打印机", listOf(13, 4, 7, 10, 1, 12, 9, 3, 14, 16)),
            "ascii case ignored" to Triple(EntryQuery(searchText = "wifi"), "wifi", listOf(13, 2, 4, 10, 1, 12, 9, 3, 11, 14, 16)),
            "duplicate case variant keeps phrase first" to Triple(EntryQuery(searchText = "WiFi wifi"), "WiFi wifi", listOf(11, 13, 2, 4, 10, 1, 12, 9, 3, 14, 16)),
            "underscore is literal" to Triple(EntryQuery(searchText = "_"), "_", listOf(7L)),
            "percent underscore are literal" to Triple(EntryQuery(searchText = "%_"), "%_", listOf(7L)),
            "backslash and colon are literal" to Triple(EntryQuery(searchText = "C:\\资料"), "C:\\资料", listOf(7L)),
            "full literal sample" to Triple(EntryQuery(searchText = "50%_C:\\资料"), "50%_C:\\资料", listOf(7L)),
            "image filter" to Triple(EntryQuery(searchText = "打印机 WiFi", hasImage = true), "打印机 WiFi + 有图", listOf(4L)),
            "resource tag filter" to Triple(EntryQuery(searchText = "打印机 WiFi", selectedTags = listOf(TagType.RESOURCE)), "打印机 WiFi + 资料", listOf(12L)),
            "date range filter" to Triple(EntryQuery(searchText = "打印机 WiFi", startDate = 150L, endDate = 650L), "打印机 WiFi + 时段", listOf(12, 9, 10, 1)),
            "unicode case is not folded" to Triple(EntryQuery(searchText = "ä"), "ä", emptyList()),
            "overflow returns nothing" to Triple(EntryQuery(searchText = "a".repeat(8193)), "超长查询", emptyList()),
        )

        val jsonCases = cases.map { (name, value) ->
            val (query, label, expected) = value
            val captured = capture(query)
            linkedMapOf(
                "name" to name,
                "label" to label,
                "searchText" to query.searchText,
                "startDate" to query.startDate,
                "endDate" to query.endDate,
                "hasImage" to query.hasImage,
                "tags" to query.selectedTags.map { it.name },
                "sql" to captured.sql,
                "bindArgs" to captured.args,
                "expectedIds" to expected,
            )
        }

        val fixture = linkedMapOf(
            "schemaSql" to listOf(ENTRIES_SCHEMA, TAGS_SCHEMA),
            "entries" to FIXTURE_ENTRIES.map { entry ->
                linkedMapOf(
                    "id" to entry.id,
                    "content" to entry.content,
                    "imagePath" to entry.imagePath,
                    "type" to entry.type,
                    "createdAt" to entry.createdAt,
                    "updatedAt" to entry.createdAt,
                    "isDeleted" to if (entry.deleted) 1 else 0,
                )
            },
            "tags" to FIXTURE_TAGS,
            "cases" to jsonCases,
        )

        val out = File("build/test-fixtures/lightweight-search.json")
        out.parentFile.mkdirs()
        out.writeText(Gson().toJson(fixture))
        assertTrue(out.length() > 0)
    }

    private data class FixtureEntry(
        val id: Long,
        val content: String,
        val createdAt: Long,
        val imagePath: String? = null,
        val deleted: Boolean = false,
        val type: String = "NORMAL",
    )

    companion object {
        private const val ENTRIES_SCHEMA =
            "CREATE TABLE IF NOT EXISTS `entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `content` TEXT NOT NULL, `imagePath` TEXT, `type` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `summaryStart` INTEGER, `summaryEnd` INTEGER, `imageDescription` TEXT, `isDeleted` INTEGER NOT NULL, `summaryModel` TEXT, `entryRevision` INTEGER NOT NULL)"
        private const val TAGS_SCHEMA =
            "CREATE TABLE IF NOT EXISTS `entry_tags` (`entryId` INTEGER NOT NULL, `tag` TEXT NOT NULL, PRIMARY KEY(`entryId`, `tag`))"

        /** 与 LightweightSearchTest 的固定记录一致；含删除、总结、资料、图片与 emoji 之外的边界。 */
        private val FIXTURE_ENTRIES = listOf(
            FixtureEntry(1, "打印机连接方法\nWiFi 设置步骤", 500),
            FixtureEntry(2, "WiFi 路由器说明", 900),
            FixtureEntry(3, "打印机 WiFi 一起出现", 100),
            FixtureEntry(4, "打印机 WiFi 耗材 下单", 800, imagePath = "a.webp"),
            FixtureEntry(5, "已删除 打印机 WiFi", 950, deleted = true),
            FixtureEntry(6, "总结 打印机 WiFi", 960, type = "SUMMARY"),
            FixtureEntry(7, "50%_C:\\资料 打印机", 700),
            FixtureEntry(8, "ANDROID 和 android 都算", 300),
            FixtureEntry(9, "打印机连接 WiFi 设置", 200),
            FixtureEntry(10, "打印机 前面 WiFi 后面 相隔很远", 600),
            FixtureEntry(11, "WiFi wifi 同一行", 50),
            FixtureEntry(12, "资料 打印机 WiFi 说明", 400),
            FixtureEntry(13, "打印机 WiFi", 1000),
            FixtureEntry(14, "前言".repeat(200) + "打印机 WiFi 步骤", 20),
            FixtureEntry(16, "打印机WiFi连着呢", 5),
            FixtureEntry(17, "Ä 慕尼黑", 450),
        )

        private val FIXTURE_TAGS = listOf(
            mapOf("entryId" to 12L, "tag" to "RESOURCE"),
            mapOf("entryId" to 13L, "tag" to "EVENT"),
        )
    }
}
