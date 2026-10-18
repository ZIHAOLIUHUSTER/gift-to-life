package com.gift.tolife.feature.settings

import com.gift.tolife.core.datastore.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiConfigDraftTest {
    private val stored = AppSettings(
        baseUrl = "https://stored.example", apiKey = "stored-key",
        tagModel = "tag-old", visionModel = "vision-old", summaryModel = "summary-old",
        themeMode = "dark", themeVariant = "PINK", biometricEnabled = true,
        autoSummaryEnabled = false, summaryThinking = "deep"
    )
    private val edited = AiConfigDraft.from(stored).copy(
        baseUrl = "  edited.example/v1/  ", apiKey = " edited-key ",
        tagModel = " tag-new ", visionModel = " vision-new ", summaryModel = " summary-new "
    )

    @Test
    fun `whole validation collects every error and cannot write partial settings`() {
        var writes = 0
        val validation = AiConfigValidator.save(
            edited.copy(baseUrl = "http://insecure.example", tagModel = " ", visionModel = "",
                summaryModel = "\n", summaryBaseUrl = "https://summary.example?secret=x"),
            stored
        ) { writes++ }
        assertNull(validation.normalized)
        assertEquals(setOf(AiConfigField.BASE_URL, AiConfigField.TAG_MODEL, AiConfigField.VISION_MODEL,
            AiConfigField.SUMMARY_MODEL, AiConfigField.SUMMARY_BASE_URL), validation.errors.keys)
        assertEquals(0, writes)
    }

    @Test
    fun `late summary validation failure still prevents all daily writes`() {
        val writes = mutableListOf<AppSettings>()
        val validation = AiConfigValidator.save(edited.copy(summaryBaseUrl = "http://bad.example"), stored) { writes.add(it) }
        assertNull(validation.normalized)
        assertEquals(setOf(AiConfigField.SUMMARY_BASE_URL), validation.errors.keys)
        assertTrue(writes.isEmpty())
    }

    @Test
    fun `successful save writes once with normalized seven fields preserving immediate settings`() {
        val writes = mutableListOf<AppSettings>()
        val draft = edited.copy(summaryApiKey = " summary-key ", summaryBaseUrl = "https://summary.example/v1/")
        val validation = AiConfigValidator.save(draft, stored) { writes.add(it) }
        assertTrue(validation.errors.isEmpty())
        assertEquals(1, writes.size)
        assertEquals(stored.copy(
            apiKey = "edited-key", baseUrl = "https://edited.example/v1/",
            tagModel = "tag-new", visionModel = "vision-new", summaryModel = "summary-new",
            summaryApiKey = "summary-key", summaryBaseUrl = "https://summary.example/v1/"
        ), writes.single())
        assertEquals(validation.normalized, AiConfigDraft.from(writes.single()))
        // Validation does not mutate the text the user is still editing.
        assertEquals("  edited.example/v1/  ", draft.baseUrl)
    }

    @Test
    fun `empty keys are legal on save and never restore old stored key`() {
        var result: AppSettings? = null
        val validation = AiConfigValidator.save(edited.copy(apiKey = " ", summaryApiKey = " "), stored) { result = it }
        assertNotNull(validation.normalized)
        assertEquals("", result!!.apiKey)
        assertEquals("", result!!.summaryApiKey)
    }

    @Test
    fun `write failure propagates instead of returning successful validation`() {
        var writes = 0
        assertThrows(IllegalStateException::class.java) {
            AiConfigValidator.save(edited, stored) {
                writes++
                throw IllegalStateException("write failed")
            }
        }
        assertEquals(1, writes)
    }

    @Test
    fun `missing required address and models report their own fields`() {
        val validation = AiConfigValidator.validate(edited.copy(baseUrl = "\n", tagModel = "", visionModel = " ", summaryModel = ""))
        assertEquals(setOf(AiConfigField.BASE_URL, AiConfigField.TAG_MODEL, AiConfigField.VISION_MODEL,
            AiConfigField.SUMMARY_MODEL), validation.errors.keys)
        assertNull(validation.normalized)
    }

    @Test
    fun `optional blank summary address normalizes to follow`() {
        val validation = AiConfigValidator.validate(edited.copy(summaryBaseUrl = " \n "))
        val normalized = validation.normalized!!
        assertEquals("", normalized.summaryBaseUrl)
        assertEquals("https://edited.example/v1/", normalized.baseUrl)
    }

    @Test
    fun `every field edit affects only that field and keeps original draft intact`() {
        for (field in AiConfigField.values()) {
            val changed = edited.withField(field, "new-value")
            assertEquals(edited, changed.withField(field, valueOf(edited, field)))
            assertEquals("new-value", valueOf(changed, field))
        }
    }

    @Test
    fun `all connection targets use current unsaved provider not stored values`() {
        for ((target, model) in listOf(AiModelTarget.TAG to "tag-new", AiModelTarget.VISION to "vision-new",
            AiModelTarget.SUMMARY to "summary-new")) {
            assertEquals(AiDraftConnection(model, "edited-key", "https://edited.example/v1/"),
                AiConfigValidator.connection(edited, target))
        }
        assertEquals("stored-key", stored.apiKey)
    }

    @Test
    fun `summary overrides and fallbacks are resolved per field from current draft`() {
        val explicit = edited.copy(summaryApiKey = " summary-key ", summaryBaseUrl = "https://summary.example/v1")
        assertTrue(explicit.hasSummaryOverride)
        assertEquals(AiDraftConnection("summary-new", "summary-key", "https://summary.example/v1/"),
            AiConfigValidator.connection(explicit, AiModelTarget.SUMMARY))
        assertEquals("edited-key", AiConfigValidator.connection(explicit.copy(summaryApiKey = " "), AiModelTarget.SUMMARY).apiKey)
        assertEquals("https://edited.example/v1/",
            AiConfigValidator.connection(explicit.copy(summaryBaseUrl = " "), AiModelTarget.SUMMARY).baseUrl)
        assertFalse(edited.hasSummaryOverride)
    }

    @Test
    fun `empty current key blocks every connection before request without falling back to persisted key`() {
        for (target in AiModelTarget.values()) {
            var requests = 0
            val error = assertThrows(IllegalArgumentException::class.java) {
                AiConfigValidator.connection(edited.copy(apiKey = " "), target)
                requests++
            }
            assertEquals("未配置 API Key，无法测试连接", error.message)
            assertEquals(0, requests)
        }
        // 保存仍然合法；独立总结 Key 也仍可用于单独测试总结。
        assertNotNull(AiConfigValidator.validate(edited.copy(apiKey = " ")).normalized)
        assertEquals("summary-key", AiConfigValidator.connection(
            edited.copy(apiKey = "", summaryApiKey = "summary-key"), AiModelTarget.SUMMARY
        ).apiKey)
    }

    @Test
    fun `connection validates requested target without blocking on unrelated unfinished models`() {
        val incomplete = edited.copy(visionModel = "", summaryModel = "")
        assertEquals("tag-new", AiConfigValidator.connection(incomplete, AiModelTarget.TAG).model)
        assertThrows(IllegalArgumentException::class.java) { AiConfigValidator.connection(incomplete, AiModelTarget.VISION) }
        assertThrows(IllegalArgumentException::class.java) { AiConfigValidator.connection(incomplete, AiModelTarget.SUMMARY) }
    }

    @Test
    fun `import updates draft display and testing preserves both unsaved keys and does not save`() {
        val draft = edited.copy(summaryApiKey = "unsaved-summary-key")
        val imported = draft.importConfig(ModelConfigCodec.parse(
            """{"version":3,"baseUrl":"https://import.example/v1","tagModel":"import-tag",
                "visionModel":"import-vision","summaryModel":"import-summary",
                "summaryBaseUrl":"https://import-summary.example/v1","apiKey":"must-ignore",
                "summaryApiKey":"also-ignore","themeMode":"light"}"""
        ))
        assertEquals("https://import.example/v1/", imported.baseUrl)
        assertEquals("import-tag", imported.tagModel)
        assertEquals("import-vision", imported.visionModel)
        assertEquals("import-summary", imported.summaryModel)
        assertEquals(draft.apiKey, imported.apiKey)
        assertEquals(draft.summaryApiKey, imported.summaryApiKey)
        assertEquals(AiDraftConnection("import-tag", "edited-key", "https://import.example/v1/"),
            AiConfigValidator.connection(imported, AiModelTarget.TAG))
        assertEquals(AiDraftConnection("import-summary", "unsaved-summary-key", "https://import-summary.example/v1/"),
            AiConfigValidator.connection(imported, AiModelTarget.SUMMARY))
        assertEquals("https://stored.example", stored.baseUrl)
        val writes = mutableListOf<AppSettings>()
        AiConfigValidator.save(imported, stored) { writes.add(it) }
        assertEquals(1, writes.size)
        assertEquals("https://import.example/v1/", writes.single().baseUrl)
        assertEquals("dark", writes.single().themeMode)
    }

    @Test
    fun `legacy import clears summary url while preserving current key and follows new daily address`() {
        val imported = edited.copy(summaryBaseUrl = "https://previous-summary.example").importConfig(ModelConfigCodec.parse(
            """{"version":2,"baseUrl":"https://legacy.example","tagModel":"t","visionModel":"v","summaryModel":"s"}"""
        ))
        assertEquals("", imported.summaryBaseUrl)
        assertEquals(AiDraftConnection("s", "edited-key", "https://legacy.example/"),
            AiConfigValidator.connection(imported, AiModelTarget.SUMMARY))
    }

    @Test
    fun `invalid import cannot replace existing editable draft`() {
        var draft = edited
        assertThrows(IllegalArgumentException::class.java) {
            draft = draft.importConfig(ModelConfigCodec.parse(
                """{"baseUrl":"https://import.example","tagModel":"t","visionModel":"v","summaryModel":""}"""
            ))
        }
        assertEquals(edited, draft)
    }

    private fun valueOf(draft: AiConfigDraft, field: AiConfigField) = when (field) {
        AiConfigField.BASE_URL -> draft.baseUrl
        AiConfigField.API_KEY -> draft.apiKey
        AiConfigField.TAG_MODEL -> draft.tagModel
        AiConfigField.VISION_MODEL -> draft.visionModel
        AiConfigField.SUMMARY_MODEL -> draft.summaryModel
        AiConfigField.SUMMARY_BASE_URL -> draft.summaryBaseUrl
        AiConfigField.SUMMARY_API_KEY -> draft.summaryApiKey
    }
}
