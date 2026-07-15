package com.gift.tolife.feature.record

object EntrySavePolicy {
    fun canSave(content: String, hasImage: Boolean): Boolean {
        return content.isNotBlank() || hasImage
    }
}
