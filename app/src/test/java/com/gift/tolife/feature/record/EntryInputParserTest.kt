package com.gift.tolife.feature.record

import com.gift.tolife.core.model.TagType
import org.junit.Assert.*
import org.junit.Test

class EntryInputParserTest {
    @Test
    fun ordinaryTextKeepsAutomaticClassification() {
        val parsed = EntryInputParser.parse("  今天弄懂了一个问题 \n")
        assertEquals("今天弄懂了一个问题", parsed.content)
        assertEquals("保存", parsed.mode.saveLabel)
        assertTrue(parsed.runAi)
        assertTrue(parsed.tags.isEmpty())
    }

    @Test
    fun explicitOneRemovesPrefixButDoesNotForceAnAiTag() {
        val parsed = EntryInputParser.parse("#1突然想到一个办法")
        assertEquals("突然想到一个办法", parsed.content)
        assertTrue(parsed.runAi)
        assertTrue(parsed.tags.isEmpty())
    }

    @Test
    fun resourceNeedsNoSeparator() {
        val parsed = EntryInputParser.parse("#2打印机连接方法")
        assertEquals("打印机连接方法", parsed.content)
        assertEquals(setOf(TagType.RESOURCE), parsed.tags)
        assertEquals("保存资料", parsed.mode.saveLabel)
        assertFalse(parsed.runAi)
    }

    @Test
    fun excerptNeedsNoSeparator() {
        val parsed = EntryInputParser.parse("#3值得留下的一句话")
        assertEquals("值得留下的一句话", parsed.content)
        assertEquals(setOf(TagType.EXCERPT), parsed.tags)
        assertEquals("保存文摘", parsed.mode.saveLabel)
        assertFalse(parsed.runAi)
    }

    @Test
    fun spacesTabsAndNewlinesAfterPrefixAreOptional() {
        listOf("#2方法", "#2 方法", "#2\t方法", "#2\n方法", "#2　方法").forEach {
            assertEquals(EntryInputParser.Parsed("方法", EntryInputParser.Mode.RESOURCE), EntryInputParser.parse(it))
        }
    }

    @Test
    fun prefixOnlyCannotSaveWithoutImage() {
        listOf("#1", "#2", "#3", "#2 \n　").forEach {
            val parsed = EntryInputParser.parse(it)
            assertEquals("", parsed.content)
            assertFalse(parsed.canSave(false))
        }
    }

    @Test
    fun prefixOnlyWithImageCanSaveInItsChosenMode() {
        listOf("#1", "#2", "#3").forEach {
            assertTrue(EntryInputParser.parse(it).canSave(true))
        }
        assertEquals(setOf(TagType.RESOURCE), EntryInputParser.parse("#2").tags)
        assertFalse(EntryInputParser.parse("#2").runAi)
    }

    @Test
    fun emptyInputStillAllowsPlainImageRecords() {
        val parsed = EntryInputParser.parse(" \n")
        assertFalse(parsed.canSave(false))
        assertTrue(parsed.canSave(true))
        assertTrue(parsed.runAi)
    }

    @Test
    fun multiDigitNumbersAreNotCommands() {
        listOf("#10", "#12标题", "#20", "#23", "#30", "#300", "#2２", "#2٢").forEach {
            val parsed = EntryInputParser.parse(it)
            assertEquals(it, parsed.content)
            assertEquals(EntryInputParser.Mode.AUTO, parsed.mode)
        }
    }

    @Test
    fun unknownAndIncompletePrefixesStayLiteral() {
        listOf("#", "#0", "#4资料", "##2", "＃2", "# 2", "#资料").forEach {
            assertEquals(EntryInputParser.Parsed(it, EntryInputParser.Mode.AUTO), EntryInputParser.parse(it))
        }
    }

    @Test
    fun aPrefixInTheMiddleOrOnALaterLineStaysLiteral() {
        listOf("正文 #2资料", "正文\n#3引用", "正文\n#1闪念").forEach {
            assertEquals(EntryInputParser.Parsed(it, EntryInputParser.Mode.AUTO), EntryInputParser.parse(it))
        }
    }

    @Test
    fun leadingWhitespaceDoesNotTurnLiteralTextIntoACommand() {
        assertEquals(
            EntryInputParser.Parsed("#2原文", EntryInputParser.Mode.AUTO),
            EntryInputParser.parse("  #2原文")
        )
    }

    @Test
    fun onlyTheFirstPrefixIsConsumed() {
        val parsed = EntryInputParser.parse("#2#3是正文")
        assertEquals("#3是正文", parsed.content)
        assertEquals(EntryInputParser.Mode.RESOURCE, parsed.mode)
    }

    @Test
    fun internalWhitespaceAndEmojiArePreserved() {
        val parsed = EntryInputParser.parse("#3\n📖 第一行\n\n  第二行\n")
        assertEquals("📖 第一行\n\n  第二行", parsed.content)
    }

    @Test
    fun manualModesNeverAllowAutomaticEnhancementEvenWithAiTags() {
        assertFalse(TagType.allowsAiEnhancement(listOf(TagType.RESOURCE)))
        assertFalse(TagType.allowsAiEnhancement(listOf(TagType.EXCERPT)))
        assertFalse(TagType.allowsAiEnhancement(listOf(TagType.EVENT, TagType.RESOURCE)))
        assertFalse(TagType.allowsAiEnhancement(listOf(TagType.KNOWLEDGE, TagType.EXCERPT)))
        assertTrue(TagType.allowsAiEnhancement(emptyList()))
        assertTrue(TagType.allowsAiEnhancement(TagType.AI_MANAGED))
    }

    @Test
    fun sharingIntoAnUnfinishedManualDraftDoesNotSilentlyReplaceIt() {
        val raw = "#2待补充的资料"
        assertEquals(
            RecordInputPolicy.ShareAction.QUEUE,
            RecordInputPolicy.incomingShare("外部分享", false, raw, false, false, false)
        )
        assertEquals("待补充的资料", EntryInputParser.parse(raw).content)
        assertEquals("#2待补充的资料", raw)
    }

    @Test
    fun failedSavePreservesRawPrefixAndPendingShare() {
        val raw = "#3摘录"
        EntryInputParser.parse(raw)
        assertEquals("#3摘录", raw)
        assertEquals(
            RecordInputPolicy.SaveCompletion(clearDraft = false, receivePendingShare = false),
            RecordInputPolicy.completeSave(false, 8, 8, 10, 10)
        )
    }

    @Test
    fun aModeChangeDuringSavingKeepsTheNewDraft() {
        assertEquals(
            RecordInputPolicy.SaveCompletion(clearDraft = false, receivePendingShare = false),
            RecordInputPolicy.completeSave(true, 8, 9, 10, 10)
        )
        assertEquals(EntryInputParser.Mode.EXCERPT, EntryInputParser.parse("#3新草稿").mode)
    }

    @Test
    fun existingEntryValidationDoesNotReinterpretCommandLookingText() {
        // 已有记录的编辑仍使用通用保存校验，不能把真实的 #2 正文当作新输入命令。
        assertTrue(EntrySavePolicy.canSave("#2", false))
        assertFalse(EntryInputParser.parse("#2").canSave(false))
    }
}
