package com.gift.tolife.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.gift.tolife.core.export.SnapshotInfo
import com.gift.tolife.core.ui.UiTestTags
import com.gift.tolife.core.ui.theme.GiftTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 只渲染截图同量级的假数据，不读取用户资料、不调用文件选择器或恢复操作。 */
class BackupContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val stats = SettingsViewModel.DataStats(
        totalEntries = 100, usageDays = 89, imageCount = 52, summaryCount = 10,
        imageSizeBytes = 5_662_310L, dailyAvg = 1.1f
    )

    @Test
    fun sixStatisticsAreVisibleWithoutAnExpandAction() {
        compose.setContent {
            GiftTheme(themeMode = "light") {
                BackupContent(stats, false, null, null, {}, {}, {})
            }
        }
        listOf("使用统计", "100", "89", "52", "10", "条记录", "天使用", "张图片", "篇总结", "图片占用", "日均记录")
            .forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithTag(UiTestTags.UNDO_IMPORT).assertDoesNotExist()
    }

    @Test
    fun exportImportAndUndoKeepTheirOwnCallbacks() {
        var exports = 0
        var imports = 0
        var undos = 0
        compose.setContent {
            GiftTheme {
                BackupContent(
                    stats, false, null, SnapshotInfo(1_700_000_000_000L, 100),
                    onExport = { exports++ }, onImport = { imports++ }, onUndo = { undos++ }
                )
            }
        }
        compose.onNodeWithTag(UiTestTags.EXPORT_BACKUP).performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag(UiTestTags.IMPORT_BACKUP).performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag(UiTestTags.UNDO_IMPORT).performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, exports)
            assertEquals(1, imports)
            assertEquals(1, undos)
        }
    }

    @Test
    fun busyStateDisablesAllDataActionsInDarkMode() {
        compose.setContent {
            GiftTheme(themeMode = "dark") {
                BackupContent(
                    stats, true, "正在导出……", SnapshotInfo(1_700_000_000_000L, 100), {}, {}, {}
                )
            }
        }
        listOf(UiTestTags.EXPORT_BACKUP, UiTestTags.IMPORT_BACKUP, UiTestTags.UNDO_IMPORT)
            .forEach { compose.onNodeWithTag(it).performScrollTo().assertIsNotEnabled() }
        compose.onNodeWithText("正在导出……").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun narrowLargeTextLayoutKeepsStatisticsAndActionsReachable() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                GiftTheme {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        BackupContent(stats, false, null, null, {}, {}, {})
                    }
                }
            }
        }
        listOf("条记录", "天使用", "张图片", "篇总结", "图片占用", "日均记录")
            .forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
        listOf(UiTestTags.EXPORT_BACKUP, UiTestTags.IMPORT_BACKUP).forEach {
            compose.onNodeWithTag(it).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun emptyStatisticsDoNotHideBackupActions() {
        compose.setContent {
            GiftTheme {
                BackupContent(SettingsViewModel.DataStats(), false, null, null, {}, {}, {})
            }
        }
        compose.onNodeWithTag(UiTestTags.EXPORT_BACKUP).performScrollTo().assertIsEnabled()
        compose.onNodeWithTag(UiTestTags.IMPORT_BACKUP).performScrollTo().assertIsEnabled()
        compose.onNodeWithText("0 B").performScrollTo().assertIsDisplayed()
    }
}
