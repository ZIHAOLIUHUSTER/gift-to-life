package com.gift.tolife.feature.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gift.tolife.BuildConfig
import com.gift.tolife.core.common.DateFormats
import com.gift.tolife.core.common.SiblingApp
import com.gift.tolife.core.ui.UiTestTags
import com.gift.tolife.core.ui.component.Eyebrow
import com.gift.tolife.core.ui.component.PageHeader
import com.gift.tolife.core.ui.component.PaperCard
import com.gift.tolife.core.ui.component.SectionLabel
import com.gift.tolife.core.ui.theme.LifeType
import com.gift.tolife.core.ui.theme.ThemeExtended
import com.gift.tolife.core.ui.theme.ThemeVariant
import com.gift.tolife.widget.GiftWidgets
import kotlinx.coroutines.launch
import java.util.Calendar
import com.gift.tolife.core.ui.theme.PageGutter
import com.gift.tolife.core.ui.theme.ContentPadding

private enum class SettingsPage { MAIN, MODEL_CONFIG, DATA_MANAGE, RECYCLE_BIN, APPEARANCE, ABOUT }

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    var currentPage by remember { mutableStateOf(SettingsPage.MAIN) }
    val stats by viewModel.stats.collectAsState()
    val dataStats by viewModel.dataStats.collectAsState()

    BackHandler(enabled = currentPage != SettingsPage.MAIN) {
        currentPage = SettingsPage.MAIN
    }

    when (currentPage) {
        SettingsPage.MAIN -> SettingsMainPage(
            stats = stats,
            onRefreshStats = viewModel::refreshStats,
            onNavigate = { currentPage = it },
        )
        SettingsPage.MODEL_CONFIG -> ModelConfigPage(viewModel, onBack = { currentPage = SettingsPage.MAIN })
        SettingsPage.DATA_MANAGE -> {
            LaunchedEffect(Unit) {
                viewModel.refreshDataStats()
                viewModel.refreshSnapshot()
            }
            DataManagePage(viewModel, dataStats = dataStats, onBack = { currentPage = SettingsPage.MAIN })
        }
        SettingsPage.RECYCLE_BIN -> RecycleBinPage(viewModel, onBack = { currentPage = SettingsPage.MAIN })
        SettingsPage.APPEARANCE -> AppearancePage(viewModel, onBack = { currentPage = SettingsPage.MAIN })
        SettingsPage.ABOUT -> AboutPage(onBack = { currentPage = SettingsPage.MAIN })
    }
}

// ===== 设置主页 =====

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsMainPage(
    stats: SettingsViewModel.StatsData,
    onRefreshStats: () -> Unit,
    onNavigate: (SettingsPage) -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PageHeader(title = "设置", eyebrow = "GIFT TO LIFE")
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = PageGutter, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { MonthSummaryCard(stats = stats, onRefresh = onRefreshStats) }
            item {
                SectionLabel("服务")
                SettingsGroup {
                    SettingsRow(icon = Icons.Rounded.Settings, title = "AI 配置", description = "识别、总结与连接测试") {
                        onNavigate(SettingsPage.MODEL_CONFIG)
                    }
                    GroupDivider()
                    SettingsRow(icon = Icons.Rounded.Backup, title = "备份与恢复") {
                        onNavigate(SettingsPage.DATA_MANAGE)
                    }
                    GroupDivider()
                    SettingsRow(icon = Icons.Rounded.Delete, title = "回收站") {
                        onNavigate(SettingsPage.RECYCLE_BIN)
                    }
                }
            }
            item {
                SectionLabel("应用")
                SettingsGroup {
                    SettingsRow(icon = Icons.Rounded.Palette, title = "外观") {
                        onNavigate(SettingsPage.APPEARANCE)
                    }
                    GroupDivider()
                    SettingsRow(icon = Icons.Rounded.Info, title = "关于") {
                        onNavigate(SettingsPage.ABOUT)
                    }
                }
            }
        }
    }
}

/**
 * 本月摘要 + 热力图（2026-10-18 精修）。
 *
 * 大数字是这一页唯一的「仪表读数」；连续天数、活跃天数退成右侧小字，
 * 热力图保留原来的月历结构，但加了一行极弱的星期标记和「今天」细环。
 */
@Composable
private fun MonthSummaryCard(stats: SettingsViewModel.StatsData, onRefresh: () -> Unit) {
    LaunchedEffect(Unit) { onRefresh() }

    val cal = Calendar.getInstance()
    val month = cal.get(Calendar.MONTH) + 1
    val today = cal.get(Calendar.DAY_OF_MONTH)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val daysElapsed = today.coerceAtLeast(1)
    val activeDays = stats.dailyCounts.count { it.value > 0 }
    val dailyAvg = stats.monthlyCount.toFloat() / daysElapsed

    PaperCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = 1.dp,
        borderAlpha = 0.55f,
    ) {
        Column(modifier = Modifier.padding(ContentPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("${month} 月")
                Spacer(Modifier.weight(1f))
                if (stats.streakCount > 0) {
                    Text(
                        text = "连续 ${stats.streakCount} 天",
                        style = LifeType.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${stats.monthlyCount}",
                    style = LifeType.number,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "%.1f".format(dailyAvg),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = " 条/日",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                    Text(
                        text = "活跃 $activeDays 天",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf("日", "一", "二", "三", "四", "五", "六").forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        style = LifeType.meta.copy(fontSize = 10.sp, lineHeight = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            MonthHeatmap(
                daysInMonth = daysInMonth,
                firstDayOffset = firstDayOfMonthOffset(),
                dailyCounts = stats.dailyCounts,
                today = today,
            )
        }
    }
}

/** 周日起算的当月首日偏移（与 App 原有 Calendar 对齐方式一致）。 */
private fun firstDayOfMonthOffset(): Int {
    val firstDay = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
    return firstDay.get(Calendar.DAY_OF_WEEK) - 1
}

@Composable
private fun MonthHeatmap(
    daysInMonth: Int,
    firstDayOffset: Int,
    dailyCounts: Map<Int, Int>,
    today: Int,
) {
    val totalCells = firstDayOffset + daysInMonth
    val rows = (totalCells + 6) / 7
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    val cellShape = RoundedCornerShape(3.dp)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (col in 0 until 7) {
                    val day = row * 7 + col - firstDayOffset + 1
                    if (day in 1..daysInMonth) {
                        val count = dailyCounts[day] ?: 0
                        val alpha = when {
                            count <= 0 -> 0f
                            count == 1 -> 0.24f
                            count in 2..3 -> 0.44f
                            count in 4..6 -> 0.66f
                            else -> 0.90f
                        }
                        val isToday = day == today
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(16.dp)
                                .clip(cellShape)
                                .background(
                                    if (alpha == 0f) emptyColor
                                    else MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                                )
                                .then(
                                    if (isToday) {
                                        Modifier.border(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                            cellShape,
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ===== 通用组件 =====

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    PaperCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = 0.dp,
        borderAlpha = 0.55f,
    ) {
        Column(content = content)
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = ContentPadding),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    description: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = ContentPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = ThemeExtended.current.accentText,
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    SectionLabel(text)
}

// ===== AI 配置 =====

/** 默认备份文件名带时间戳，避免重复导出时误覆盖上一份备份。 */
private fun defaultExportFileName(): String {
    val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(java.util.Date())
    return "gift_backup_$stamp.gtlbackup"
}

/**
 * AI 配置（2026-09-27 重构，spec §5.4）。
 *
 * 默认只留：自动回顾 / 日常识别（地址、Key、标签模型、视觉模型）/ 总结模型 / 一个底部保存。
 * 独立总结服务、思考强度、导入导出收进「高级设置」——普通用户看不到实现细节。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ModelConfigPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val draft = uiState.aiDraft ?: return
    var advanced by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.validationAttempt) {
        if (AiConfigField.SUMMARY_BASE_URL in uiState.aiErrors) advanced = true
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "没有通知权限，回顾写好后不会提醒你", Toast.LENGTH_LONG).show()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportModelConfig(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importModelConfig(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PageHeader(title = "AI 配置", onBack = onBack, showDivider = true)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = PageGutter, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 自动回顾
                SettingsGroup {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(ContentPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "自动回顾",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "每周一 / 每月初写好，提醒你",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = uiState.settings.autoSummaryEnabled,
                            onCheckedChange = { checked ->
                                viewModel.updateAutoSummaryEnabled(checked)
                                if (checked && android.os.Build.VERSION.SDK_INT >= 33) {
                                    notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        )
                    }
                }

                Text(
                    text = "自动回顾与思考强度即时生效；地址、Key 和模型保存后生效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SettingsSectionLabel("日常识别")
                SettingsGroup {
                    AiDraftFieldRow(
                        state = uiState, viewModel = viewModel, field = AiConfigField.BASE_URL,
                        label = "服务地址",
                        value = draft.baseUrl,
                        placeholder = "https://api.deepseek.com",
                        keyboardType = KeyboardType.Uri,
                    )
                    GroupDivider()
                    AiDraftFieldRow(
                        state = uiState, viewModel = viewModel, field = AiConfigField.API_KEY,
                        label = "API Key",
                        value = draft.apiKey,
                        placeholder = "留空不启用日常识别",
                        isSecret = true,
                        keyboardType = KeyboardType.Password,
                    )
                    GroupDivider()
                    AiDraftFieldRow(
                        state = uiState, viewModel = viewModel, field = AiConfigField.TAG_MODEL,
                        label = "标签模型", value = draft.tagModel,
                    )
                    GroupDivider()
                    AiDraftFieldRow(
                        state = uiState, viewModel = viewModel, field = AiConfigField.VISION_MODEL,
                        label = "视觉模型", value = draft.visionModel,
                    )
                }

                SettingsSectionLabel("周志与月志")
                SettingsGroup {
                    AiDraftFieldRow(
                        state = uiState, viewModel = viewModel, field = AiConfigField.SUMMARY_MODEL,
                        label = "总结模型", value = draft.summaryModel,
                    )
                    GroupDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ContentPadding, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (draft.hasSummaryOverride) {
                                "使用独立的总结服务"
                            } else {
                                "跟随日常识别那组服务"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 高级设置：默认折叠
                SettingsGroup {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { advanced = !advanced }
                            .padding(ContentPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "高级设置",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "连接测试 · 独立服务 · 思考强度",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(if (advanced) 180f else 0f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AnimatedVisibility(visible = advanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SettingsSectionLabel("连接测试")
                        SettingsGroup {
                            ConnectionTestRow("标签模型", uiState.testingTag, uiState.testResultTag, viewModel::testTagModel)
                            GroupDivider()
                            ConnectionTestRow("视觉模型", uiState.testingVision, uiState.testResultVision, viewModel::testVisionModel)
                            GroupDivider()
                            ConnectionTestRow("总结模型", uiState.testingSummary, uiState.testResultSummary, viewModel::testSummaryModel)
                        }
                        SettingsSectionLabel("独立总结服务")
                        SettingsGroup {
                            AiDraftFieldRow(
                                state = uiState, viewModel = viewModel, field = AiConfigField.SUMMARY_BASE_URL,
                                label = "服务地址",
                                value = draft.summaryBaseUrl,
                                placeholder = "留空沿用日常识别",
                                keyboardType = KeyboardType.Uri,
                            )
                            GroupDivider()
                            AiDraftFieldRow(
                                state = uiState, viewModel = viewModel, field = AiConfigField.SUMMARY_API_KEY,
                                label = "API Key",
                                value = draft.summaryApiKey,
                                placeholder = "留空沿用日常识别",
                                isSecret = true,
                                keyboardType = KeyboardType.Password,
                            )
                        }

                        SettingsSectionLabel("思考强度")
                        SettingsGroup {
                            Column(modifier = Modifier.padding(ContentPadding)) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(
                                        "off" to "不思考",
                                        "high" to "思考",
                                        "deep" to "深度思考",
                                    ).forEach { (key, label) ->
                                        FilterChip(
                                            selected = uiState.settings.summaryThinking == key,
                                            onClick = { viewModel.updateSummaryThinking(key) },
                                            label = { Text(label) },
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = when (uiState.settings.summaryThinking) {
                                        "off" -> "最快最省，适合不想等的时候。"
                                        "deep" -> "不限制思考长度：最慢最贵，可能要几分钟。"
                                        else -> "默认：先想一下，但限制思考长度。"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        SettingsSectionLabel("配置导入导出")
                        SettingsGroup {
                            Column(modifier = Modifier.padding(ContentPadding)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { exportLauncher.launch("model_config.json") },
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                        shape = MaterialTheme.shapes.large
                                    ) { Text("导出") }
                                    OutlinedButton(
                                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                        shape = MaterialTheme.shapes.large
                                    ) { Text("导入") }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "只导出地址与模型名，不含 API Key。",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
            }

            // 全页唯一的保存动作
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = PageGutter, vertical = 14.dp)
            ) {
                Button(
                    onClick = viewModel::saveAiConfiguration,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(UiTestTags.SETTINGS_SAVE),
                    shape = MaterialTheme.shapes.large
                ) { Text("保存") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AiDraftFieldRow(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    field: AiConfigField,
    label: String,
    value: String,
    placeholder: String = "",
    isSecret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(state.validationAttempt) {
        if (state.validationAttempt > 0 && state.aiErrors.keys.firstOrNull() == field) {
            withFrameNanos { }
            focusRequester.requestFocus()
            bringIntoViewRequester.bringIntoView()
        }
    }
    AuthFieldRow(
        label = label, value = value,
        onValueChange = { viewModel.updateAiDraft(field, it) },
        placeholder = placeholder, isSecret = isSecret, keyboardType = keyboardType,
        error = state.aiErrors[field],
        fieldModifier = Modifier.focusRequester(focusRequester).bringIntoViewRequester(bringIntoViewRequester)
    )
}

@Composable
private fun AuthFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isSecret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    error: String? = null,
    fieldModifier: Modifier = Modifier,
) {
    var showSecret by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(ContentPadding)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
            isError = error != null,
            supportingText = if (error != null) { { Text(error) } } else null,
            singleLine = true,
            shape = MaterialTheme.shapes.extraSmall,
            visualTransformation = if (isSecret && !showSecret) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            trailingIcon = if (isSecret) {
                {
                    IconButton(onClick = { showSecret = !showSecret }) {
                        Icon(
                            if (showSecret) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "切换显示"
                        )
                    }
                }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
    }
}

@Composable
private fun ConnectionTestRow(
    label: String,
    testing: Boolean,
    testResult: String?,
    onTest: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = ContentPadding, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onTest, enabled = !testing, modifier = Modifier.heightIn(min = 48.dp)) {
                if (testing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (testing) "测试中" else "测试连接")
            }
        }
        if (testResult != null) {
            Text(
                text = testResult,
                style = MaterialTheme.typography.bodySmall,
                color = if (testResult.startsWith("✓")) ThemeExtended.current.success else ThemeExtended.current.danger
            )
        }
    }
}

// ===== 备份与恢复 =====

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataManagePage(
    viewModel: SettingsViewModel,
    dataStats: SettingsViewModel.DataStats,
    onBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showUndoConfirm by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()
    val isDataBusy = uiState.isExporting || uiState.isImporting || uiState.isInspecting

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) { is SettingsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message) }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let { viewModel.exportData(it) } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.previewImport(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PageHeader(title = "备份与恢复", onBack = onBack, showDivider = true)
        }
    ) { innerPadding ->
        BackupContent(
            dataStats = dataStats,
            isBusy = isDataBusy,
            busyMessage = when {
                uiState.isExporting -> "正在导出……"
                uiState.isInspecting -> "正在读取备份……"
                uiState.isImporting -> "正在导入……"
                else -> null
            },
            snapshot = uiState.snapshot,
            onExport = { exportLauncher.launch(defaultExportFileName()) },
            onImport = {
                importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
            },
            onUndo = { showUndoConfirm = true },
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        )

        // 导入预检结果确认（空备份二次强提示）
        uiState.pendingImportPreview?.let { preview ->
            val emptyBackup = preview.entryCount == 0
            val exportedAt = preview.exportedAt
            AlertDialog(
                onDismissRequest = { viewModel.cancelPendingImport() },
                title = { Text(if (emptyBackup) "⚠ 这是空备份" else "确认导入") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "备份内容：${preview.entryCount} 条记录" +
                                (if (preview.deletedCount > 0) "（含回收站 ${preview.deletedCount} 条）" else "") +
                                "，${preview.imageCount} 张图片"
                        )
                        Text(
                            "备份版本：" + (if (preview.isLegacy) "旧版 JSON（v1）" else "v2") +
                                (preview.appVersion?.let { "，App $it" } ?: "")
                        )
                        if (exportedAt != null && exportedAt > 0) {
                            Text("导出时间：${DateFormats.formatDateTime(exportedAt)}")
                        }
                        Text("导入会清空并替换当前所有记录。导入前会自动保存一份当前数据快照，可在本页撤销。")
                        if (emptyBackup) {
                            Text(
                                "这个备份里没有任何记录，继续导入会清空你现在的全部数据！",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmImport() }) {
                        Text(if (emptyBackup) "仍然清空导入" else "开始导入", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { viewModel.cancelPendingImport() }) { Text("取消") } }
            )
        }

        if (showUndoConfirm) {
            AlertDialog(
                onDismissRequest = { showUndoConfirm = false },
                title = { Text("撤销上次导入") },
                text = { Text("将用导入前的快照恢复数据，当前数据会先自动保存一份快照。确定撤销？") },
                confirmButton = {
                    TextButton(onClick = { showUndoConfirm = false; viewModel.undoImport() }) {
                        Text("撤销", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { showUndoConfirm = false }) { Text("取消") } }
            )
        }
    }
}

// ===== 回收站 =====

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecycleBinPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    var deletedEntries by remember { mutableStateOf<List<com.gift.tolife.core.model.Entry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        deletedEntries = viewModel.getDeletedEntries()
        loading = false
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) { is SettingsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PageHeader(
                title = "回收站",
                onBack = onBack,
                showDivider = true,
                actions = {
                    if (deletedEntries.isNotEmpty()) {
                        TextButton(
                            onClick = { showClearConfirm = true },
                            modifier = Modifier.testTag(UiTestTags.RECYCLE_CLEAR)
                        ) {
                            Text("清空", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("清空回收站") },
                text = { Text("将永久删除 ${deletedEntries.size} 条记录，不可恢复。确定清空？") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            viewModel.permanentlyDeleteAll()
                            deletedEntries = emptyList()
                            showClearConfirm = false
                        }
                    }) { Text("清空", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
                }
            )
        }

        if (deletedEntries.isEmpty() && !loading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(
                    "回收站为空",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(horizontal = PageGutter, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(deletedEntries, key = { it.id }) { entry ->
                    SettingsGroup {
                        Row(
                            modifier = Modifier.padding(ContentPadding),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    com.gift.tolife.core.ai.SummaryCardParser.displayTitle(entry.content),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    DateFormats.formatDayTime(entry.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        viewModel.restoreEntry(entry.id)
                                        deletedEntries = deletedEntries.filter { it.id != entry.id }
                                    }
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) { Text("恢复") }
                        }
                    }
                }
            }
        }
    }
}

// ===== 外观 =====

/**
 * 外观（2026-09-21 版式：皮肤 3×2 迷你纸张预览 + 深浅模式一排 chip）。
 * 2026-09-27 只跟随全局 token 收敛（内容卡 10dp、中性描边），版式不再改。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AppearancePage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val currentTheme = uiState.settings.themeMode
    val currentVariant = uiState.settings.themeVariant
    val dark = when (currentTheme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PageHeader(title = "外观", onBack = onBack, showDivider = true)
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PageGutter, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsGroup {
                Column(modifier = Modifier.padding(ContentPadding)) {
                    Text(
                        text = "皮肤",
                        style = LifeType.eyebrow,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    ThemeVariant.values().toList().chunked(3).forEachIndexed { index, rowItems ->
                        if (index > 0) Spacer(Modifier.height(14.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowItems.forEach { variant ->
                                SkinOptionCard(
                                    variant = variant,
                                    selected = currentVariant == variant.name,
                                    darkTheme = dark,
                                    onClick = {
                                        viewModel.updateThemeVariant(variant.name)
                                        GiftWidgets.requestRestyle(context)
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            SettingsGroup {
                Column(modifier = Modifier.padding(ContentPadding)) {
                    Text(
                        text = "深浅模式",
                        style = LifeType.eyebrow,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "system" to "跟随系统",
                            "light" to "浅色",
                            "dark" to "深色"
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = currentTheme == value,
                                onClick = {
                                    viewModel.updateThemeMode(value)
                                    GiftWidgets.requestRestyle(context)
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ===== 关于 =====

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutPage(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PageHeader(title = "关于", onBack = onBack, showDivider = true)
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = PageGutter, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 品牌块：主题色带只作小面积品牌细节
            PaperCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = ThemeExtended.current.zone,
                elevation = 0.dp,
                borderAlpha = 0.35f,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✦", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Gift To Life", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "To Life 系列 · 闪念日记",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            SettingsGroup {
                Row(
                    Modifier.fillMaxWidth().padding(ContentPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "当前版本",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SettingsGroup {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!SiblingApp.launch(context, SiblingApp.DATE_PACKAGE)) {
                                Toast.makeText(context, "未安装 Date To Life", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(ContentPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Date To Life", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "课表日程 · 课程与日程同构",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Text(
                "MIT License · 全部数据保存在本机，不上传云端",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
