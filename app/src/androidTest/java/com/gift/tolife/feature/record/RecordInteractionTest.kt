package com.gift.tolife.feature.record

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.TagType
import com.gift.tolife.core.ui.UiTestTags
import com.gift.tolife.core.ui.theme.GiftTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 只渲染内存中的假记录，不访问 Repository、模型配置或真实记录。 */
class RecordInteractionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun composerKeepsReadableActionsAtLargeFontScale() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                GiftTheme {
                    EntryComposer(
                        value = "一句随手记下的想法",
                        onValueChange = {},
                        pendingImageUri = null,
                        isSaving = false,
                        onSave = {},
                        onPickImage = {},
                        onClearImage = {},
                    )
                }
            }
        }
        compose.onNodeWithTag(UiTestTags.COMPOSER_SAVE)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("图片").assertIsDisplayed()
    }

    @Test
    fun resourceCanBeSelectedManuallyAndSavedWithExistingTags() {
        var saved: EntryEditDraft? = null
        compose.setContent {
            GiftTheme {
                EditEntryBottomSheet(
                    originalEntry = Entry(id = 1, content = "打印机连接方法"),
                    originalTags = setOf(TagType.KNOWLEDGE),
                    onSave = { saved = it },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("资料").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("资料").assertIsSelected()
        compose.onNodeWithText("保存").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(setOf(TagType.KNOWLEDGE, TagType.RESOURCE), saved?.tags)
            assertEquals("打印机连接方法", saved?.content)
        }
    }

    @Test
    fun editIsReachableWithoutScrollingToTheEndOfALongPreview() {
        var edits = 0
        compose.setContent {
            GiftTheme {
                EntryPreviewSheet(
                    entry = Entry(content = "这是需要滚动才能读完的测试记录。\n".repeat(100)),
                    tags = emptyList(),
                    onEdit = { edits++ },
                    onDismiss = {},
                    onDelete = {},
                )
            }
        }
        compose.onNodeWithText("编辑").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, edits) }
    }
}
