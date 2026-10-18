package com.gift.tolife.feature.memory

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.gift.tolife.core.common.DateFormats
import com.gift.tolife.core.common.ReviewDeepLink
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.TagType
import com.gift.tolife.core.ui.UiTestTags
import com.gift.tolife.core.ui.component.AppEmptyState
import com.gift.tolife.core.ui.component.Eyebrow
import com.gift.tolife.core.ui.component.PageHeader
import com.gift.tolife.core.ui.component.PaperCard
import com.gift.tolife.core.ui.component.pressScale
import com.gift.tolife.core.ui.theme.ContentPadding
import com.gift.tolife.core.ui.theme.LifeType
import com.gift.tolife.core.ui.theme.PageGutter
import com.gift.tolife.core.ui.theme.ThemeExtended
import com.gift.tolife.feature.record.EntryPreviewSheet
import java.io.File
import kotlinx.coroutines.flow.collectLatest

/**
 * 随机回顾卡的固定外壳高度。
 *
 * 关键：这个值**与内容无关**。用户连点「再抽一条」时，卡片尺寸、元信息行、
 * 按钮位置全程不变，只有内容区在换 —— 见 docs/visual-refactor-spec.md §5.2.3。
 */
private val RANDOM_CARD_HEIGHT = 340.dp

/** 有图时图片固定占的高度（永远预留，不因图片加载完成而撑开卡片）。 */
private val RANDOM_IMAGE_HEIGHT = 140.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    onNavigateToWeekSummary: () -> Unit = {},
    onNavigateToMonthSummary: () -> Unit = {},
    onNavigateToOnThisDay: () -> Unit = {},
    onNavigateToRecord: () -> Unit = {},
    onOpenSummary: (Long) -> Unit = {},
    randomVM: RandomReviewViewModel = hiltViewModel(),
    summaryVM: SummaryViewModel = hiltViewModel()
) {
    val randomState by randomVM.state.collectAsState()
    val summaryState by summaryVM.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var previewEntry by remember { mutableStateOf<Entry?>(null) }
    var previewTags by remember { mutableStateOf<List<TagType>>(emptyList()) }
    var previewImagePath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(randomVM) {
        randomVM.events.collectLatest { event ->
            when (event) {
                is MemoryEntryEvent.Deleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "已移至回收站",
                        actionLabel = "撤销",
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) randomVM.restoreEntry(event.entryId)
                }
                is MemoryEntryEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    LaunchedEffect(Unit) {
        summaryVM.events.collect { event ->
            when (event) {
                is SummaryEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is SummaryEvent.OfferRetry -> snackbarHostState.showSnackbar(event.message)
                // 其余事件（确认重生成、打开牌桌）由各自的总结页实例处理
                is SummaryEvent.ConfirmRegenerate, is SummaryEvent.OpenDeck -> {}
            }
        }
    }

    // 随机回顾小组件深链：收到条目 ID → 加载并弹出详情预览
    LaunchedEffect(Unit) {
        ReviewDeepLink.entryId.collect { id ->
            if (id != null) {
                randomVM.showEntry(id)
                ReviewDeepLink.consume()
            }
        }
    }

    // 深链/预览请求加载完成后弹出详情
    LaunchedEffect(randomState.pendingPreview) {
        val pending = randomState.pendingPreview ?: return@LaunchedEffect
        previewEntry = pending.first
        previewTags = pending.second
        randomVM.consumePendingPreview()
    }

    val randomEntry = randomState.entry
    val hasAnything = randomEntry != null ||
        summaryState.weekSummaries.isNotEmpty() ||
        summaryState.monthSummaries.isNotEmpty()
    val monthLabel = remember { DateFormats.formatMonthYear(System.currentTimeMillis()) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PageHeader(title = "回忆", eyebrow = monthLabel)
        }
    ) { innerPadding ->
        if (!hasAnything) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                AppEmptyState(
                    title = "还没有记录",
                    description = "去记录页写下第一句",
                    actionLabel = "去记录",
                    onAction = onNavigateToRecord,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            ) {
                if (randomEntry != null) {
                    item(key = "random") {
                        RandomReviewCard(
                            entry = randomEntry,
                            tags = randomState.tags,
                            onRefresh = randomVM::fetchRandom,
                            onClick = {
                                previewEntry = randomEntry
                                previewTags = randomState.tags
                            }
                        )
                    }
                }

                item(key = "entries") {
                    Spacer(Modifier.height(16.dp))
                    MemoryEntriesGroup(
                        weekValue = weekSummaryStatus(summaryState),
                        monthValue = monthSummaryStatus(summaryState),
                        onNavigateToWeekSummary = {
                            summaryState.currentWeekSummary?.let { onOpenSummary(it.id) }
                                ?: onNavigateToWeekSummary()
                        },
                        onNavigateToMonthSummary = {
                            summaryState.currentMonthSummary?.let { onOpenSummary(it.id) }
                                ?: onNavigateToMonthSummary()
                        },
                        onWeekHistory = onNavigateToWeekSummary.takeIf { summaryState.currentWeekSummary != null },
                        onMonthHistory = onNavigateToMonthSummary.takeIf { summaryState.currentMonthSummary != null },
                        onNavigateToOnThisDay = onNavigateToOnThisDay,
                    )
                }
            }
        }
    }

    // 预览弹层
    if (previewEntry != null) {
        EntryPreviewSheet(
            entry = previewEntry!!,
            tags = previewTags,
            onDismiss = { previewEntry = null },
            onDelete = {
                previewEntry?.let { randomVM.deleteEntry(it.id) }
                previewEntry = null
            },
            onImageClick = { previewImagePath = previewEntry!!.imagePath }
        )
    }

    // 图片预览 Dialog
    if (previewImagePath != null) {
        com.gift.tolife.feature.record.ImagePreviewDialog(
            imagePath = previewImagePath!!,
            onDismiss = { previewImagePath = null }
        )
    }
}

private fun weekSummaryStatus(state: SummaryState): String = when {
    state.currentWeekSummary != null -> "上周 · 已写好"
    state.recentWeekCount > 0 -> "上周 · ${state.recentWeekCount} 条"
    else -> "还没有记录"
}

private fun monthSummaryStatus(state: SummaryState): String = when {
    state.currentMonthSummary != null -> "上月 · 已写好"
    state.recentMonthCount > 0 -> "上月 · ${state.recentMonthCount} 条"
    else -> "还没有记录"
}

/**
 * 随机回顾卡（定高外壳，2026-10-18 精修）。
 *
 * 结构没有变（固定 340dp，连点不跳），但把它做成「一叠纸里的最上面一张」：
 * - 后面两张略窄、略低的底纸，只在底部露出 5dp / 10dp 的纸边；
 * - 短句居中，用主色引号做轻点睛；长文左对齐，超出时给「读全文」；
 * - 「再抽一条」图标转 360°，配一次轻触感。
 */
@Composable
private fun RandomReviewCard(
    entry: Entry,
    tags: List<TagType>,
    onRefresh: () -> Unit,
    onClick: () -> Unit = {}
) {
    val hasImage = !entry.imagePath.isNullOrBlank()
    val content = entry.content
    val isShort = content.length <= 48 && !hasImage
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val shape = RoundedCornerShape(18.dp)
    val accent = ThemeExtended.current.accentText
    val refreshInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    var turns by remember { mutableIntStateOf(0) }
    val rotation by animateFloatAsState(
        targetValue = turns * 360f,
        animationSpec = tween(420),
        label = "refreshRotation",
    )
    val quoteColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val backingColor = if (ThemeExtended.current.dark) {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val quoteText = remember(content, isShort, quoteColor) {
        if (!isShort) {
            AnnotatedString(content)
        } else {
            buildAnnotatedString {
                val mark = SpanStyle(color = quoteColor)
                if (!content.startsWith("「")) withStyle(mark) { append("「") }
                append(content)
                if (!content.endsWith("」")) withStyle(mark) { append("」") }
            }
        }
    }
    val bodyStyle = if (isShort) LifeType.quote else MaterialTheme.typography.bodyLarge

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageGutter)
            .height(RANDOM_CARD_HEIGHT + 12.dp)
    ) {
        // 下面两张纸：只在底部露出纸边，暗示「一叠记忆」
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .height(RANDOM_CARD_HEIGHT)
                .offset(y = 10.dp),
            shape = shape,
            color = backingColor,
            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        ) {}
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(RANDOM_CARD_HEIGHT)
                .offset(y = 5.dp),
            shape = shape,
            color = backingColor,
            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        ) {}

        PaperCard(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(RANDOM_CARD_HEIGHT)
                .clip(shape)
                .pressScale(cardInteraction, pressedScale = 0.992f)
                .clickable(
                    interactionSource = cardInteraction,
                    indication = LocalIndication.current,
                    onClick = onClick,
                ),
            shape = shape,
            elevation = 5.dp,
            borderAlpha = 0.55f,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 20.dp, top = 17.dp, bottom = 12.dp)
            ) {
                Eyebrow("随机回顾")

                Spacer(Modifier.height(12.dp))

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clipToBounds()
                ) {
                    val maxWidthPx = with(density) { maxWidth.roundToPx() }
                    val lineHeightDp = with(density) { bodyStyle.lineHeight.toDp() }
                    val linesThatFit = (maxHeight.value / lineHeightDp.value).toInt()
                    val maxLines = linesThatFit.coerceIn(2, if (isShort) 5 else 6)
                    val overflow = remember(quoteText, bodyStyle, maxWidthPx, maxLines) {
                        content.isNotBlank() && measurer.measure(
                            text = quoteText,
                            style = bodyStyle,
                            constraints = Constraints(maxWidth = maxWidthPx)
                        ).lineCount > maxLines
                    }

                    if (hasImage) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = File(entry.imagePath!!),
                                contentDescription = "回顾图片",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(RANDOM_IMAGE_HEIGHT)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            if (content.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = content,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = quoteText,
                                style = bodyStyle,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = maxLines,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = if (isShort) TextAlign.Center else TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (overflow) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "读全文",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent,
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                )
                Spacer(Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val meta = remember(entry.createdAt, tags) {
                        val labels = tags.take(2).map { it.label }
                        (listOf(DateFormats.formatDayTime(entry.createdAt)) + labels).joinToString(" · ")
                    }
                    Text(
                        text = meta,
                        style = LifeType.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = refreshInteraction,
                                indication = LocalIndication.current,
                            ) {
                                turns += 1
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRefresh()
                            }
                            .padding(horizontal = 6.dp)
                            .testTag(UiTestTags.MEMORY_REFRESH),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp).rotate(rotation),
                            tint = accent
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "再抽一条",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = accent
                        )
                    }
                }
            }
        }
    }
}

/** 周志 / 月志 / 那年今日：一个分组内容面里的三行。 */
@Composable
private fun MemoryEntriesGroup(
    weekValue: String,
    monthValue: String,
    onNavigateToWeekSummary: () -> Unit,
    onNavigateToMonthSummary: () -> Unit,
    onNavigateToOnThisDay: () -> Unit,
    onWeekHistory: (() -> Unit)?,
    onMonthHistory: (() -> Unit)?,
) {
    PaperCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageGutter),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = 0.dp,
        borderAlpha = 0.55f,
    ) {
        Column {
            MemoryRow(
                icon = Icons.Rounded.DateRange,
                title = "周志",
                value = weekValue,
                onClick = onNavigateToWeekSummary,
                onHistory = onWeekHistory,
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = ContentPadding),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            MemoryRow(
                icon = Icons.Rounded.CalendarMonth,
                title = "月志",
                value = monthValue,
                onClick = onNavigateToMonthSummary,
                onHistory = onMonthHistory,
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = ContentPadding),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            OnThisDayRow(onViewAll = onNavigateToOnThisDay)
        }
    }
}

@Composable
private fun MemoryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    onHistory: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = ContentPadding),
        verticalAlignment = Alignment.CenterVertically,
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
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = LifeType.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        if (onHistory != null) {
            IconButton(onClick = onHistory, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Rounded.History,
                    contentDescription = "${title}历史",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun OnThisDayRow(onViewAll: () -> Unit) {
    val viewModel: OnThisDayViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    val month = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
    val day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)

    val value = when {
        state.loading -> "…"
        state.yearEntries.isEmpty() -> "暂无"
        else -> {
            val total = state.yearEntries.values.sumOf { it.size }
            "${state.yearEntries.keys.size} 年 · $total 条"
        }
    }

    MemoryRow(
        icon = Icons.Rounded.Today,
        title = "那年今日",
        value = "${month}月${day}日 · $value",
        onClick = onViewAll,
    )
}
