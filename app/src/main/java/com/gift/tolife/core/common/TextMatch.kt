package com.gift.tolife.core.common

/**
 * 搜索命中的**纯函数**（可单测，不依赖 Compose）。
 *
 * 用途：列表里的记录在搜索模式下把命中的字符区间标出来，省得用户自己找。
 * 规则：
 * - 忽略 ASCII 大小写（中文按原样匹配）；
 * - 命中区间**不重叠**，从前向后扫描；
 * - 空白 query 直接返回空（搜索框为空时不高亮）。
 */
object TextMatch {

    /** 返回 [text] 中所有 [query] 出现的区间（左闭右开，用 `IntRange` 表示起始 until 结束）。 */
    fun ranges(text: String, query: String): List<IntRange> {
        if (query.isBlank() || text.isEmpty() || query.length > text.length) return emptyList()
        val result = mutableListOf<IntRange>()
        var from = 0
        while (from <= text.length - query.length) {
            val index = text.indexOf(query, startIndex = from, ignoreCase = true)
            if (index < 0) break
            result += index until (index + query.length)
            from = index + query.length
        }
        return result
    }
}
