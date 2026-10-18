package com.gift.tolife.feature.record

import com.gift.tolife.core.model.TagType

/** 仅用于新记录输入；不修改编辑器原文，也不重新解释已有记录或导入内容。 */
object EntryInputParser {
    enum class Mode(val tag: TagType?, val saveLabel: String) {
        AUTO(null, "保存"),
        RESOURCE(TagType.RESOURCE, "保存资料"),
        EXCERPT(TagType.EXCERPT, "保存文摘")
    }

    data class Parsed(val content: String, val mode: Mode) {
        val tags: Set<TagType> get() = mode.tag?.let { setOf(it) } ?: emptySet()
        val runAi: Boolean get() = mode == Mode.AUTO

        fun canSave(hasImage: Boolean): Boolean = EntrySavePolicy.canSave(content, hasImage)
    }

    fun parse(input: String): Parsed {
        // 只识别绝对开头的 #1/#2/#3；#20、#23 等编号（含 Unicode 数字）不是指令。
        if (input.length < 2 || input[0] != '#' || input.getOrNull(2)?.isDigit() == true) {
            return Parsed(input.trim(), Mode.AUTO)
        }
        val mode = when (input[1]) {
            '1' -> Mode.AUTO
            '2' -> Mode.RESOURCE
            '3' -> Mode.EXCERPT
            else -> return Parsed(input.trim(), Mode.AUTO)
        }
        return Parsed(input.substring(2).trim(), mode)
    }
}
