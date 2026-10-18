package com.gift.tolife.core.common

/**
 * 搜索命中的**纯函数**（可单测，不依赖 Compose）。
 *
 * 用途：列表里的记录在搜索模式下把命中的字符区间标出来，省得用户自己找。
 * 规则：
 * - 忽略 ASCII 大小写（中文按原样匹配）；
 * - 使用与 SQL 一致的空白切词，每个词均高亮，重叠区间合并；
 * - 空白 query 直接返回空（搜索框为空时不高亮）；
 * - 搜索预览截取命中附近，原始正文不改写。
 */
object TextMatch {
    /** 预览会把换行折叠成空格，U+2028/U+2029/U+0085 等也属于会换行的字符。 */
    private val previewBreaks = Regex("[\\r\\n\\t\\u000B\\u000C\\u0085\\u2028\\u2029]+")

    /** 返回所有关键词命中的区间（左闭右开，用 `起始 until 结束` 表示）。 */
    fun ranges(text: String, query: String): List<IntRange> {
        val terms = SearchText.parse(query).terms
        if (terms.isEmpty() || text.isEmpty()) return emptyList()
        val folded = SearchText.foldAscii(text)
        val hits = mutableListOf<IntRange>()
        terms.forEach { term ->
            // 预览折叠空白后，长查询（>64 词回退整段）里的制表符/换行也要归一才能命中。
            val needle = SearchText.foldAscii(previewBreaks.replace(term, " "))
            var from = 0
            while (from <= folded.length - needle.length) {
                val index = folded.indexOf(needle, startIndex = from)
                if (index < 0) break
                hits += index until (index + needle.length)
                from = index + needle.length
            }
        }
        val merged = mutableListOf<IntRange>()
        hits.sortedBy { it.first }.forEach { hit ->
            val previous = merged.lastOrNull()
            if (previous != null && hit.first <= previous.last) {
                merged[merged.lastIndex] = previous.first..maxOf(previous.last, hit.last)
            } else {
                merged += hit
            }
        }
        return merged
    }

    /** 命中前留少量上下文；优先展示整句命中，否则展示最早关键词。 */
    fun preview(text: String, query: String, maxChars: Int = 160, contextBefore: Int = 24): String {
        require(maxChars > 0 && contextBefore >= 0 && contextBefore < maxChars)
        val search = SearchText.parse(query)
        if (search.terms.isEmpty() || text.isEmpty()) return text
        val folded = SearchText.foldAscii(text)
        val phraseStart = folded.indexOf(SearchText.foldAscii(search.phrase))
        val hit = if (phraseStart >= 0) {
            phraseStart to search.phrase.length
        } else {
            search.terms.mapNotNull { term ->
                val index = folded.indexOf(SearchText.foldAscii(term))
                if (index >= 0) index to term.length else null
            }.minByOrNull { it.first }
        } ?: return text

        var start = maxOf(0, hit.first - contextBefore)
        var end = minOf(text.length.toLong(), maxOf(start.toLong() + maxChars, hit.first.toLong() + hit.second)).toInt()
        // Kotlin 下标是 UTF-16，不能从代理对中间截断 emoji 等字符。
        if (start > 0 && text[start].isLowSurrogate() && text[start - 1].isHighSurrogate()) start--
        if (end < text.length && text[end].isLowSurrogate() && text[end - 1].isHighSurrogate()) end++
        val excerpt = text.substring(start, end)
            .replace(previewBreaks, " ")
            .trim()
        return (if (start > 0) "…" else "") + excerpt + (if (end < text.length) "…" else "")
    }
}
