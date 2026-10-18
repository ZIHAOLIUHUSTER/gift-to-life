package com.gift.tolife.core.common

/**
 * 把用户手写的记录原文变成"能直接显示"的纯文本。
 *
 * 记录里常有 markdown 痕迹（`## 标题`、`**加粗**`、`> 引用`、`- 列表`），
 * 直接摆进卡片会露出符号（2026-09-26 真机截图里就有 `## 在不确定中学习靠近：…`）。
 */
object PlainText {

    private val HEADING = Regex("(?m)^[ \\t]{0,3}#{1,6}[ \\t]*")
    private val BULLET = Regex("(?m)^[ \\t]{0,3}(?:[-*+]|[0-9]{1,2}[.)])[ \\t]+")
    private val QUOTE = Regex("(?m)^[ \\t]{0,3}>[ \\t]?")
    private val RULE = Regex("(?m)^[ \\t]{0,3}(?:-{3,}|\\*{3,}|_{3,})[ \\t]*$")
    private val EMPHASIS = Regex("\\*\\*|__|~~|`")
    private val BLANK_LINES = Regex("\\n{3,}")
    private val SPACES = Regex("[ \\t]{2,}")

    /** 去符号、收空白，保留段落换行。 */
    fun clean(raw: String): String {
        if (raw.isBlank()) return ""
        return raw
            .replace("\r\n", "\n")
            .replace(HEADING, "")
            .replace(QUOTE, "")
            .replace(RULE, "")
            .replace(BULLET, "")
            .replace(EMPHASIS, "")
            .replace(SPACES, " ")
            .replace(BLANK_LINES, "\n\n")
            .trim()
    }

    /** 一行式预览：压掉换行，超长截断并补省略号。 */
    fun preview(raw: String, maxChars: Int = 90): String {
        val flat = clean(raw).replace("\n", " ").trim()
        if (flat.length <= maxChars) return flat
        return flat.take(maxChars).trimEnd() + "…"
    }
}
