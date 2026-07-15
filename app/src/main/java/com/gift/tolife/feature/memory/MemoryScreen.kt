package com.gift.tolife.feature.memory

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import com.gift.tolife.R
import com.gift.tolife.core.common.TimeUtil
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.TagType
import com.gift.tolife.core.ui.UiTestTags
import com.gift.tolife.feature.record.EntryPreviewSheet
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    onNavigateToWeekSummary: () -> Unit = {},
    onNavigateToMonthSummary: () -> Unit = {},
    randomVM: RandomReviewViewModel = hiltViewModel(),
    summaryVM: SummaryViewModel = hiltViewModel()
) {
    val randomState by randomVM.state.collectAsState()
    val summaryState by summaryVM.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var previewEntry by remember { mutableStateOf<Entry?>(null) }
    var previewImagePath by remember { mutableStateOf<String?>(null) }

    val configuration = LocalConfiguration.current
    val stageHeight = (configuration.screenHeightDp * 0.7f).dp

    LaunchedEffect(Unit) {
        summaryVM.events.collect { event ->
            when (event) {
                is SummaryEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("回忆") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        if (randomState.entry == null && (summaryState.weekSummaries + summaryState.monthSummaries).isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "还没有记录",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "去记录页写点什么吧",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 随机回顾卡片
                val randomEntry = randomState.entry
                if (randomEntry != null) {
                    item(key = "random") {
                        RandomReviewCard(
                            entry = randomEntry,
                            tags = randomState.tags,
                            stageHeight = stageHeight,
                            onRefresh = randomVM::fetchRandom,
                            onClick = { previewEntry = randomEntry }
                        )
                    }
                }

                // 总结与归档预览
                val summaryEntries = summaryState.weekSummaries + summaryState.monthSummaries
                val latestSummary = summaryEntries.maxByOrNull { it.createdAt }
                if (latestSummary != null) {
                    item(key = "summary_preview") {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("最近总结", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(8.dp))
                                Text(latestSummary.content.take(80) + "…", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                        }
                    }
                }

                // 总结入口（独立于随机卡片）
                item(key = "summary_actions") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onNavigateToWeekSummary,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        ) { Text("周总结") }
                        OutlinedButton(
                            onClick = onNavigateToMonthSummary,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        ) { Text("月总结") }
                    }
                }

                // 历史总结列表
                val allSummaries = summaryState.weekSummaries + summaryState.monthSummaries
                if (allSummaries.isNotEmpty()) {
                    item(key = "summary_header") {
                        Text(
                            "历史总结",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    items(allSummaries, key = { it.id }) { entry ->
                        SummaryCard(entry = entry)
                    }
                }
            }
        }
    }

    // 预览弹层
    if (previewEntry != null) {
        EntryPreviewSheet(
            entry = previewEntry!!,
            tags = randomState.tags,
            onEdit = { previewEntry = null },
            onDismiss = { previewEntry = null },
            onDelete = { previewEntry = null },
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

@Composable
private fun RandomReviewCard(
    entry: Entry,
    tags: List<TagType>,
    stageHeight: Dp,
    onRefresh: () -> Unit,
    onClick: () -> Unit = {}
) {
    var refreshEnabled by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(stageHeight)
            .clickable(onClick = onClick)
            .testTag(UiTestTags.MEMORY_CARD),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Crossfade(targetState = entry.id, animationSpec = tween(300)) { _ ->
            Column(modifier = Modifier.padding(28.dp)) {
                // 标题
                Text(
                    "随机回顾",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 内容区（文字 + 图片）
                Column {
                    // 内容（带引号装饰）
                    Text(
                        "「${entry.content}」",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 图片
                    if (!entry.imagePath.isNullOrBlank()) {
                        AsyncImage(
                            model = File(entry.imagePath),
                            contentDescription = "回顾图片",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .heightIn(max = 180.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 标签 + 时间（居中）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (tags.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            tags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        tag.label,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Text(
                        formatTime(entry.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            refreshEnabled = false
                            onRefresh()
                        },
                        enabled = refreshEnabled,
                        modifier = Modifier.testTag(UiTestTags.MEMORY_REFRESH)
                    ) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("再抽一条", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    LaunchedEffect(refreshEnabled) {
        if (!refreshEnabled) {
            delay(300)
            refreshEnabled = true
        }
    }
}

@Composable
internal fun SummaryCard(entry: Entry) {
    val timeLabel = if (entry.summaryStart != null && entry.summaryEnd != null) {
        if (entry.summaryEnd!! - entry.summaryStart!! > 25L * 24 * 60 * 60 * 1000) {
            TimeUtil.formatMonth(entry.summaryStart!!, entry.summaryEnd!!)
        } else {
            TimeUtil.formatWeek(entry.summaryStart!!, entry.summaryEnd!!)
        }
    } else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (timeLabel != null) {
                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                        Text(timeLabel, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (!entry.summaryModel.isNullOrBlank()) {
                    Text(entry.summaryModel!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(entry.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 3)
            Spacer(modifier = Modifier.height(8.dp))
            Text(formatTime(entry.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
