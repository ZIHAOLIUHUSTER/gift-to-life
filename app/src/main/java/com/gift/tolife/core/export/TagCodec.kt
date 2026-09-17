package com.gift.tolife.core.export

import com.gift.tolife.core.model.TagType

/**
 * 备份里标签的编解码。
 *
 * - `tagIds`：枚举名（稳定标识，改名/改文案不受影响）
 * - `tags`：中文文案（兼容旧版本读取，人也能看懂）
 *
 * 新备份同时写两者；导入时优先用 `tagIds`，缺失则回退到 `tags`（旧备份、v1 JSON）。
 * 未知标签会被丢弃并计数，由导入回执告知用户，而不是静默消失。
 */
object TagCodec {

    data class Encoded(val ids: List<String>, val labels: List<String>)

    data class Decoded(val tags: List<TagType>, val dropped: Int)

    fun encode(tags: List<TagType>): Encoded {
        val distinct = tags.distinct()
        return Encoded(
            ids = distinct.map { it.name },
            labels = distinct.map { it.label }
        )
    }

    fun decode(ids: List<String>?, labels: List<String>?): Decoded {
        val idList = ids.orEmpty().filter { it.isNotBlank() }
        if (idList.isNotEmpty()) {
            val byName = TagType.entries.associateBy { it.name }
            var dropped = 0
            val result = LinkedHashSet<TagType>()
            idList.forEach { id ->
                val tag = byName[id]
                if (tag != null) result.add(tag) else dropped++
            }
            return Decoded(result.toList(), dropped)
        }
        val byLabel = TagType.entries.associateBy { it.label }
        var dropped = 0
        val result = LinkedHashSet<TagType>()
        labels.orEmpty().filter { it.isNotBlank() }.forEach { label ->
            val tag = byLabel[label]
            if (tag != null) result.add(tag) else dropped++
        }
        return Decoded(result.toList(), dropped)
    }
}
