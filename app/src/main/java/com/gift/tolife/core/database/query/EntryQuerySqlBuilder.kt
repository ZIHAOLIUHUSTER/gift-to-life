package com.gift.tolife.core.database.query

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.gift.tolife.core.model.EntryQuery

object EntryQuerySqlBuilder {
    fun build(query: EntryQuery): SupportSQLiteQuery {
        val where = StringBuilder("e.isDeleted = 0")
        val bindArgs = mutableListOf<Any>()

        if (query.searchText.isNotBlank()) {
            bindArgs.add("%${query.searchText}%")
            where.append(" AND e.content LIKE ?")
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

        val sql = """
            SELECT e.id, e.content, e.imagePath, e.type,
                   e.createdAt, e.updatedAt, e.imageDescription,
                   GROUP_CONCAT(t.tag) AS tagsCsv
            FROM entries e
            LEFT JOIN entry_tags t ON t.entryId = e.id
            WHERE $where
            GROUP BY e.id
            ORDER BY e.createdAt DESC, e.id DESC
        """.trimIndent()

        return SimpleSQLiteQuery(sql, bindArgs.toTypedArray())
    }
}
