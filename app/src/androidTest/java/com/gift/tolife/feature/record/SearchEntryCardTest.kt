package com.gift.tolife.feature.record

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gift.tolife.core.common.TextMatch
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.ui.theme.GiftTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 假数据列表行；不访问记录库、图片或模型服务。 */
class SearchEntryCardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun endOfLongRecordIsPreviewedAndClickStillUsesOriginalEntry() {
        val entry = Entry(id = 1, content = "前言\n".repeat(100) + "打印机连接 WiFi 的步骤")
        val query = "打印机 WiFi"
        var opened: Entry? = null
        compose.setContent {
            GiftTheme {
                EntryCard(entry = entry, query = query, onClick = { opened = entry })
            }
        }
        val preview = TextMatch.preview(entry.content, query)
        val node = compose.onNodeWithText(preview, useUnmergedTree = true)
        node.assertIsDisplayed().assertTextEquals(preview).performClick()
        compose.runOnIdle { assertEquals(entry.content, opened?.content) }
        val annotated = node.fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertEquals(2, annotated.spanStyles.size)
    }

    @Test
    fun overlappingWordsRenderOneHighlightWithoutDuplicatingContent() {
        compose.setContent {
            GiftTheme {
                EntryCard(Entry(content = "打印机连接"), query = "打印 打印机 机连", onClick = {})
            }
        }
        val node = compose.onNodeWithText("打印机连接", useUnmergedTree = true)
        node.assertIsDisplayed().assertTextEquals("打印机连接")
        val annotated = node.fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertEquals(1, annotated.spanStyles.size)
        assertEquals(0, annotated.spanStyles.single().start)
        assertEquals(4, annotated.spanStyles.single().end)
    }

    @Test
    fun clearingSearchRestoresOriginalPreviewAndRemovesHighlights() {
        val original = "前言\n".repeat(100) + "打印机 WiFi"
        var query by mutableStateOf("打印机 WiFi")
        compose.setContent {
            GiftTheme {
                EntryCard(Entry(content = original), query = query, onClick = {})
            }
        }
        compose.onNodeWithText(TextMatch.preview(original, query), useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { query = "" }
        val node = compose.onNodeWithText(original, useUnmergedTree = true)
        node.assertTextEquals(original)
        assertEquals(emptyList<Any>(), node.fetchSemanticsNode().config[SemanticsProperties.Text].single().spanStyles)
    }

    @Test
    fun reversedWordsAreHighlightedSeparatelyWithAsciiCaseMatching() {
        compose.setContent {
            GiftTheme {
                EntryCard(Entry(content = "WIFI连接到打印机"), query = "打印机 wifi", onClick = {})
            }
        }
        val node = compose.onNodeWithText("WIFI连接到打印机", useUnmergedTree = true)
        node.assertIsDisplayed()
        val annotated = node.fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertEquals(listOf(0 to 4, 7 to 10), annotated.spanStyles.map { it.start to it.end })
    }
}
