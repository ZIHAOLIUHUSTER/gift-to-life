package com.gift.tolife.feature.record

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.gift.tolife.core.ui.UiTestTags
import com.gift.tolife.core.ui.theme.GiftTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 只操作内存草稿，不调用真实保存、图片选择器或 AI。 */
class EntryPrefixInteractionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun saveLabelFollowsPrefixWithoutRewritingTheDraft() {
        var text by mutableStateOf("#2打印机连接方法")
        compose.setContent {
            GiftTheme {
                EntryComposer(text, { text = it }, null, false, {}, {}, {})
            }
        }
        compose.onNodeWithText("保存资料").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag(UiTestTags.COMPOSER_INPUT).assertTextEquals("#2打印机连接方法")
        compose.onNodeWithTag(UiTestTags.COMPOSER_INPUT).performTextReplacement("#3摘录")
        compose.onNodeWithText("保存文摘").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag(UiTestTags.COMPOSER_INPUT).assertTextEquals("#3摘录")
        compose.onNodeWithTag(UiTestTags.COMPOSER_INPUT).performTextReplacement("#1一个想法")
        compose.onNodeWithText("保存").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag(UiTestTags.COMPOSER_INPUT).assertTextEquals("#1一个想法")
    }

    @Test
    fun prefixOnlyNeedsAnImageAndKeepsTheManualMode() {
        var image by mutableStateOf<Uri?>(null)
        compose.setContent {
            GiftTheme {
                EntryComposer("#2", {}, image, false, {}, {}, { image = null })
            }
        }
        compose.onNodeWithText("保存资料").assertIsDisplayed().assertIsNotEnabled()
        // 不存在的测试 URI 只用于占位，不读取或修改用户图片。
        compose.runOnIdle { image = Uri.parse("content://gift.test.invalid/fixture") }
        compose.onNodeWithText("保存资料").assertIsEnabled()
        compose.onNodeWithTag(UiTestTags.COMPOSER_INPUT).assertTextEquals("#2")
        compose.onNodeWithContentDescription("移除图片").performClick()
        compose.onNodeWithText("保存资料").assertIsNotEnabled()
    }

    @Test
    fun savingDoesNotAcceptAnotherClickOrEraseThePrefix() {
        var saving by mutableStateOf(false)
        var clicks = 0
        compose.setContent {
            GiftTheme {
                EntryComposer("#3原文", {}, null, saving, { clicks++; saving = true }, {}, {})
            }
        }
        compose.onNodeWithTag(UiTestTags.COMPOSER_SAVE).performClick()
        compose.onNodeWithTag(UiTestTags.COMPOSER_SAVE).assertIsNotEnabled()
        compose.onNodeWithTag(UiTestTags.COMPOSER_INPUT).assertTextEquals("#3原文")
        compose.runOnIdle { assertEquals(1, clicks); saving = false }
        compose.onNodeWithText("保存文摘").assertIsEnabled()
        compose.onNodeWithTag(UiTestTags.COMPOSER_INPUT).assertTextEquals("#3原文")
    }

    @Test
    fun multiDigitNumberStaysLiteralAndUsesTheNormalSaveAction() {
        compose.setContent {
            GiftTheme {
                EntryComposer("#23原始编号", {}, null, false, {}, {}, {})
            }
        }
        compose.onNodeWithText("保存").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag(UiTestTags.COMPOSER_INPUT).assertTextEquals("#23原始编号")
    }
}
