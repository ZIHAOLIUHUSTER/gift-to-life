package com.gift.tolife.core.model

enum class TagType(val label: String) {
    FLASH_THOUGHT("闪念"),
    EVENT("事记"),
    EMOTION("情绪"),
    KNOWLEDGE("知识"),
    EXCERPT("文摘"),
    RESOURCE("资料");

    companion object {
        /** AI 仅管辖固定四个标签；文摘、资料由用户手动选择，重打标签时保留。 */
        val AI_MANAGED: List<TagType> = listOf(FLASH_THOUGHT, EVENT, EMOTION, KNOWLEDGE)

        /** 资料、文摘采用手动分类，不自动上传正文或图片做识别/打标签。 */
        fun allowsAiEnhancement(tags: Collection<TagType>): Boolean =
            RESOURCE !in tags && EXCERPT !in tags
    }
}
