package com.gift.tolife.core.export

import com.gift.tolife.core.model.TagType
import org.junit.Assert.assertEquals
import org.junit.Test

class TagCodecTest {

    @Test
    fun `encode writes both stable ids and human labels`() {
        val encoded = TagCodec.encode(listOf(TagType.FLASH_THOUGHT, TagType.EXCERPT))
        assertEquals(listOf("FLASH_THOUGHT", "EXCERPT"), encoded.ids)
        assertEquals(listOf("闪念", "文摘"), encoded.labels)
    }

    @Test
    fun `decode prefers ids over labels`() {
        val decoded = TagCodec.decode(listOf("EVENT"), listOf("闪念"))
        assertEquals(listOf(TagType.EVENT), decoded.tags)
        assertEquals(0, decoded.dropped)
    }

    @Test
    fun `decode falls back to labels for legacy backups`() {
        val decoded = TagCodec.decode(emptyList(), listOf("闪念", "文摘"))
        assertEquals(listOf(TagType.FLASH_THOUGHT, TagType.EXCERPT), decoded.tags)
        assertEquals(0, decoded.dropped)
    }

    @Test
    fun `decode counts unknown tags instead of silently dropping everything`() {
        val decoded = TagCodec.decode(listOf("EVENT", "FUTURE_TAG"), null)
        assertEquals(listOf(TagType.EVENT), decoded.tags)
        assertEquals(1, decoded.dropped)
    }

    @Test
    fun `decode handles null fields from hand-edited manifests`() {
        val decoded = TagCodec.decode(null, null)
        assertEquals(emptyList<TagType>(), decoded.tags)
        assertEquals(0, decoded.dropped)
    }

    @Test
    fun `resource is manual only and restores from stable id or legacy label`() {
        assertEquals("资料", TagType.RESOURCE.label)
        org.junit.Assert.assertFalse(TagType.RESOURCE in TagType.AI_MANAGED)
        org.junit.Assert.assertFalse(TagType.EXCERPT in TagType.AI_MANAGED)
        val encoded = TagCodec.encode(listOf(TagType.RESOURCE))
        assertEquals(listOf("RESOURCE"), encoded.ids)
        assertEquals(listOf("资料"), encoded.labels)
        assertEquals(listOf(TagType.RESOURCE), TagCodec.decode(encoded.ids, encoded.labels).tags)
        assertEquals(listOf(TagType.RESOURCE), TagCodec.decode(emptyList(), encoded.labels).tags)
    }

    @Test
    fun `round trip keeps all tags`() {
        val all = TagType.entries.toList()
        val encoded = TagCodec.encode(all)
        val decoded = TagCodec.decode(encoded.ids, encoded.labels)
        assertEquals(all, decoded.tags)
        assertEquals(0, decoded.dropped)
    }
}
