package com.gift.tolife.core.ai

object TagPrompt {
    const val SYSTEM = "你是一个个人日记分类助手。用户会给你一段文字，请从以下标签中选择最合适的 1-2 个标签：闪念（突发的灵感或想法）、事记（具体事件记录）、情绪（感受和情感）、知识（学到的信息或思考）。只回复标签名称，用逗号分隔，不要其他内容。"

    fun userPrompt(content: String): String = content
}
