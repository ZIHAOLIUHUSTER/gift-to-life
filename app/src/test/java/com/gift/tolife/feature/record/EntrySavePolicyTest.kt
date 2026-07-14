package com.gift.tolife.feature.record

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal object EntrySavePolicy {
    fun canSave(content: String, hasImage: Boolean): Boolean {
        return content.isNotBlank() || hasImage
    }
}

class EntrySavePolicyTest {
    @Test
    fun imageOnlyEntryCanBeSaved() {
        assertTrue(EntrySavePolicy.canSave("", hasImage = true))
    }

    @Test
    fun emptyEntryCannotBeSaved() {
        assertFalse(EntrySavePolicy.canSave("", hasImage = false))
    }

    @Test
    fun textOnlyEntryCanBeSaved() {
        assertTrue(EntrySavePolicy.canSave("hello", hasImage = false))
    }

    @Test
    fun textAndImageEntryCanBeSaved() {
        assertTrue(EntrySavePolicy.canSave("hello", hasImage = true))
    }
}
