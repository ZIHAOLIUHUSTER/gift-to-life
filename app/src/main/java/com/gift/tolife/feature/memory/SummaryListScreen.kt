package com.gift.tolife.feature.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gift.tolife.core.ai.SummaryPeriod
import com.gift.tolife.core.common.DateFormats
import com.gift.tolife.core.common.TimeUtil
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.ui.component.AppEmptyState
import com.gift.tolife.core.ui.component.PageHeader
import com.gift.tolife.core.ui.component.PaperCard
import com.gift.tolife.core.ui.theme.LifeType
import com.gift.tolife.core.ui.theme.PageGutter
import com.gift.tolife.core.ui.theme.ThemeExtended
import com.gift.tolife.core.ui.theme.ContentPadding
import kotlinx.coroutines.launch

/**
 * 周志/月志的入口页：上方是"写一份"的动作，下方是历史列表（点开进牌桌）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryListScreen(
    period: SummaryPeriod,
    onBack: () -> Unit,
    onOpenDeck: (Long) -> Unit,
    viewModel: SummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmRegenerate by remember { mutableStateOf<String?>(null) }
    val isWeek = period == SummaryPeriod.WEEK

    LaunchedEffect(Unit) { viewModel.refreshCounts() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SummaryEvent.ShowMessage -> launch { snackbarHostState.showSnackbar(event.message) }
                is SummaryEvent.ConfirmRegenerate -> confirmRegenerate = event.generatedAt
                is SummaryEvent.OpenDeck -> onOpenDeck(event.entryId)
                is SummaryEvent.OfferRetry -> launch {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = "再试一次",
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.retry()
                }
            }
        }
    }

    val summaries = (if (isWeek) state.weekSummaries else state.monthSummaries)
        .sortedByDescending { it.summaryStart ?: 0L }
    val current = if (isWeek) state.currentWeekSummary else state.currentMonthSummary
    val recordCount = if (isWeek) state.recentWeekCount else state.recentMonthCount
    val range = if (isWeek) TimeUtil.previousWeekRange() else TimeUtil.previousMonthRange()
    val busy = summaryGenerationBusy(state, period, range)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PageHeader(
                title = if (isWeek) "周志" else "月志",
                onBack = onBack,
                showDivider = true,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = PageGutter, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "action") {
                GenerateActionCard(
                    isWeek = isWeek,
                    recordCount = recordCount,
                    existing = current,
                    busy = busy,
                    onGenerate = { viewModel.generate(period) },
                    onOpen = { current?.let { onOpenDeck(it.id) } },
                )
            }

            if (summaries.isEmpty()) {
                item(key = "empty") {
                    AppEmptyState(
                        title = if (isWeek) "还没有周志" else "还没有月志",
                        description = if (isWeek) {
                            "每个周一的回顾会出现在这里"
                        } else {
                            "每个月初的回顾会出现在这里"
                        },
                    )
                }
            }

            items(summaries, key = { it.id }) { entry ->
                SummaryCard(entry = entry, onClick = { onOpenDeck(entry.id) })
            }
        }
    }

    if (confirmRegenerate != null) {
        AlertDialog(
            onDismissRequest = { confirmRegenerate = null },
            title = { Text("重新写一次？") },
            text = { Text("这个周期已经写过一份（${confirmRegenerate}）。重新生成会覆盖它，并消耗一次 API 调用。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRegenerate = null
                    viewModel.generate(period, force = true)
                }) { Text("重新写") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRegenerate = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun GenerateActionCard(
    isWeek: Boolean,
    recordCount: Int,
    existing: Entry?,
    busy: Boolean,
    onGenerate: () -> Unit,
    onOpen: () -> Unit,
) {
    PaperCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = 1.dp,
        borderAlpha = 0.55f,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                if (isWeek) "上周的你" else "上月的你",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = when {
                    existing != null && busy -> "正在整理… · 现有这份仍可阅读"
                    existing != null -> "已经写好"
                    busy -> "正在整理…"
                    recordCount == 0 -> "这段时间还没有记录"
                    recordCount <= 2 -> "只有 $recordCount 条记录，也可以写一段短的"
                    else -> "$recordCount 条记录已经等着了"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (busy) {
                Text(
                    "可以离开，写好后提醒你",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (existing != null) {
                    Button(
                        onClick = onOpen,
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = MaterialTheme.shapes.large,
                    ) { Text("看这份") }
                    TextButton(
                        onClick = onGenerate,
                        enabled = !busy && recordCount > 0,
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = MaterialTheme.shapes.large,
                    ) { Text("重写") }
                } else {
                    Button(
                        onClick = onGenerate,
                        enabled = !busy && recordCount > 0,
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(if (isWeek) "写上周的周志" else "写上月的月志")
                    }
                }
            }
        }
    }
}

/** 历史列表里的一行：结构化内容显示关键词，旧数据退化成正文首行。 */
@Composable
internal fun SummaryCard(entry: Entry, onClick: () -> Unit = {}) {
    val timeLabel = summaryPeriodLabel(entry)
    val preview = summaryPreview(entry)

    PaperCard(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        elevation = 0.dp,
        borderAlpha = 0.55f,
    ) {
        Row(
            modifier = Modifier.padding(ContentPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .width(14.dp)
                                .height(1.5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(ThemeExtended.current.accentText.copy(alpha = 0.8f))
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            timeLabel,
                            style = LifeType.eyebrow,
                            color = ThemeExtended.current.accentText,
                        )
                    }
                    Text(
                        DateFormats.formatDateTime(entry.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (preview.first.isNotBlank()) {
                    Text(
                        preview.first,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    preview.second,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

internal fun summaryPeriodLabel(entry: Entry): String {
    val start = entry.summaryStart ?: return "回顾"
    val end = entry.summaryEnd ?: return "回顾"
    return if (end - start > 25L * 24 * 60 * 60 * 1000) {
        TimeUtil.monthLabelForRange(start, end)
    } else {
        "${TimeUtil.year(start)} / ${TimeUtil.formatWeek(start, end)}"
    }
}

/** 列表预览：(标题, 摘要)。旧的纯文本总结只取首段，不显示原始 JSON。 */
internal fun summaryPreview(entry: Entry): Pair<String, String> {
    val deck = com.gift.tolife.core.ai.SummaryCardParser.parse(entry.content)
    if (deck != null) {
        val letterSnippet = deck.letter.replace("\n", " ").take(60)
        return deck.keyword to letterSnippet
    }
    val plain = entry.content
        .replace(Regex("\\*\\*"), "")
        .replace("\n", " ")
        .trim()
    return "" to plain.take(60)
}
