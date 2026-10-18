package com.gift.tolife.core.database.query

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.gift.tolife.core.common.SearchText
import com.gift.tolife.core.model.EntryQuery

object EntryQuerySqlBuilder {
    fun build(query: EntryQuery): SupportSQLiteQuery {
        val where = StringBuilder("e.isDeleted = 0 AND e.type = 'NORMAL'")
        val bindArgs = mutableListOf<Any>()

        val search = SearchText.parse(query.searchText)
        if (search.overflow) {
            // 超长查询直接无匹配，不触发 SQLite 的 LIKE 模式长度异常。
            where.append(" AND 0")
        } else {
            search.terms.forEach { term ->
                bindArgs.add(SearchText.likePattern(term))
                where.append(" AND e.content LIKE ? ESCAPE '\\'")
            }
        }
        query.startDate?.let {
            bindArgs.add(it)
            where.append(" AND e.createdAt >= ?")
        }
        query.endDate?.let {
            bindArgs.add(it)
            where.append(" AND e.createdAt <= ?")
        }
        query.hasImage?.let { hasImage ->
            if (hasImage) {
                where.append(" AND e.imagePath IS NOT NULL AND e.imagePath != ''")
            } else {
                where.append(" AND (e.imagePath IS NULL OR e.imagePath = '')")
            }
        }
        if (query.selectedTags.isNotEmpty()) {
            val placeholders = query.selectedTags.joinToString(",") { "?" }
            bindArgs.addAll(query.selectedTags.map { it.name })
            where.append(" AND e.id IN (SELECT DISTINCT entryId FROM entry_tags WHERE tag IN ($placeholders))")
        }

        val order = StringBuilder()
        // 整句连续命中优先；去重后只剩一个词时，只有原查询确实多词（如「WiFi wifi」）才加分。
        if (search.terms.isNotEmpty() &&
            (search.terms.size > 1 || search.phrase != search.terms[0])
        ) {
            order.append("CASE WHEN e.content LIKE ? ESCAPE '\\' THEN 0 ELSE 1 END ASC, ")
            bindArgs.add(SearchText.likePattern(search.phrase))
        }
        if (search.terms.size > 1) {
            // 用首次命中的覆盖跨度作低成本接近度；SQL 排序先于 Paging LIMIT/OFFSET。
            val ends = search.terms.map { term ->
                bindArgs.add(SearchText.foldAscii(term))
                "(instr(lower(e.content), ?) + ${term.codePointCount(0, term.length)})"
            }
            val starts = search.terms.map { term ->
                bindArgs.add(SearchText.foldAscii(term))
                "instr(lower(e.content), ?)"
            }
            order.append("(max(${ends.joinToString(", ")}) - min(${starts.joinToString(", ")})) ASC, ")
        }
        order.append("e.createdAt DESC, e.id DESC")

        val sql = """
            SELECT e.id, e.content, e.imagePath, e.type,
                   e.createdAt, e.updatedAt, e.imageDescription,
                   GROUP_CONCAT(t.tag) AS tagsCsv
            FROM entries e
            LEFT JOIN entry_tags t ON t.entryId = e.id
            WHERE $where
            GROUP BY e.id
            ORDER BY $order
        """.trimIndent()

        return SimpleSQLiteQuery(sql, bindArgs.toTypedArray())
    }
}
