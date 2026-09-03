package com.gift.tolife.core.model

enum class TagType(val label: String) {
    FLASH_THOUGHT("闪念"),
    EVENT("事记"),
    EMOTION("情绪"),
    KNOWLEDGE("知识"),
    EXCERPT("文摘");

    companion object {
        /** AI 打标签管辖的固定四个标签；EXCERPT（文摘）为用户手动专属，AI 永不产出、重打标签时永不覆盖 */
        val AI_MANAGED: List<TagType> = listOf(FLASH_THOUGHT, EVENT, EMOTION, KNOWLEDGE)
    }
}
