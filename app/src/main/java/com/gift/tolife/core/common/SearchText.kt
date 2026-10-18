package com.gift.tolife.core.common

/** SQL 匹配、排序与预览共用的轻量查询规则，不使用分词词库。 */
object SearchText {
    data class Query(
        val terms: List<String>,
        val phrase: String,
        /** 查询超过 [MAX_QUERY_LENGTH]：不构造 LIKE，避免 SQLite 模式长度异常。 */
        val overflow: Boolean = false,
    )

    private val whitespace = Regex("[\\s\\p{Z}\\u0085]+")
    private const val MAX_TERMS = 64

    /** 与 SQLite 默认 LIKE 模式上限（50 000 字节）留出安全余量。 */
    const val MAX_QUERY_LENGTH = 8192

    fun parse(query: String): Query {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Query(emptyList(), "")
        if (trimmed.length > MAX_QUERY_LENGTH) return Query(emptyList(), "", overflow = true)
        val terms = whitespace.splitToSequence(trimmed)
            .filter { it.isNotEmpty() }
            .distinctBy(::foldAscii)
            .take(MAX_TERMS + 1)
            .toList()
        // 不丢弃超限关键词：退回原有整段匹配，避免 SQLite 变量/表达式深度超限。
        if (terms.size > MAX_TERMS) return Query(listOf(trimmed), trimmed)
        return Query(terms, whitespace.replace(trimmed, " "))
    }

    /** 与 SQLite 默认 LIKE / lower() 一致，只折叠 ASCII 英文字母。 */
    fun foldAscii(text: String): String = buildString(text.length) {
        text.forEach { append(if (it in 'A'..'Z') it + ('a' - 'A') else it) }
    }

    /** 搜索内容始终为字面文本，百分号、下划线及反斜杠不是查询语法。 */
    fun likePattern(text: String): String = "%" + text
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_") + "%"
}
