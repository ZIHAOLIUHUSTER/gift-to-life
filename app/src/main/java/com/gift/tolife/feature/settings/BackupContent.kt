package com.gift.tolife.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gift.tolife.core.common.DateFormats
import com.gift.tolife.core.export.SnapshotInfo
import com.gift.tolife.core.ui.UiTestTags
import com.gift.tolife.core.ui.component.PaperCard
import com.gift.tolife.core.ui.theme.ContentPadding
import com.gift.tolife.core.ui.theme.LifeType
import com.gift.tolife.core.ui.theme.PageGutter

/** 纯展示层：不持有 ViewModel，不读取真实记录，也不执行备份或恢复。 */
@Composable
internal fun BackupContent(
    dataStats: SettingsViewModel.DataStats,
    isBusy: Boolean,
    busyMessage: String?,
    snapshot: SnapshotInfo?,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PageGutter, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        PaperCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            elevation = 0.dp,
            borderAlpha = 0.55f,
        ) {
            Column(modifier = Modifier.padding(ContentPadding)) {
                Text(
                    text = "使用统计",
                    style = LifeType.eyebrow,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(18.dp))
                BackupStatsRow(
                    leftValue = dataStats.totalEntries.toString(), leftLabel = "条记录",
                    rightValue = dataStats.usageDays.toString(), rightLabel = "天使用",
                    prominent = true
                )
                BackupStatsDivider()
                BackupStatsRow(
                    leftValue = dataStats.imageCount.toString(), leftLabel = "张图片",
                    rightValue = dataStats.summaryCount.toString(), rightLabel = "篇总结"
                )
                BackupStatsDivider()
                BackupStatsRow(
                    leftValue = formatBackupSize(dataStats.imageSizeBytes), leftLabel = "图片占用",
                    rightValue = "%.1f 条".format(dataStats.dailyAvg), rightLabel = "日均记录"
                )
            }
        }

        // 操作直接放在纸面上，不再在按钮外套第二层描边卡片。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onExport,
                enabled = !isBusy,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(UiTestTags.EXPORT_BACKUP),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                shape = MaterialTheme.shapes.large
            ) { Text("导出备份") }
            OutlinedButton(
                onClick = onImport,
                enabled = !isBusy,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(UiTestTags.IMPORT_BACKUP),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                shape = MaterialTheme.shapes.large
            ) { Text("导入恢复") }
        }

        if (isBusy && busyMessage != null) {
            Row(
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = busyMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (snapshot != null) {
            Column {
                TextButton(
                    onClick = onUndo,
                    enabled = !isBusy,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(UiTestTags.UNDO_IMPORT),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 12.dp)
                ) { Text("撤销上次导入") }
                Text(
                    text = "${DateFormats.formatShortDateTime(snapshot.createdAt)} · ${snapshot.entryCount} 条记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BackupStatsRow(
    leftValue: String,
    leftLabel: String,
    rightValue: String,
    rightLabel: String,
    prominent: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BackupStatItem(leftValue, leftLabel, prominent, Modifier.weight(1f))
        BackupStatItem(rightValue, rightLabel, prominent, Modifier.weight(1f))
    }
}

@Composable
private fun BackupStatItem(value: String, label: String, prominent: Boolean, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = if (prominent) MaterialTheme.typography.displayMedium else MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BackupStatsDivider() {
    Spacer(Modifier.height(14.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Spacer(Modifier.height(14.dp))
}

private fun formatBackupSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
