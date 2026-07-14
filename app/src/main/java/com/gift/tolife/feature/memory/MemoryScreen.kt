package com.gift.tolife.feature.memory

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.gift.tolife.R
import com.gift.tolife.core.common.TimeUtil
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.TagType
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
                            onRefresh = randomVM::fetchRandom,
                            isGenerating = summaryState.isGenerating,
                            canGenerateWeek = summaryState.canGenerateWeek,
                            canGenerateMonth = summaryState.canGenerateMonth,
                            onWeekSummary = onNavigateToWeekSummary,
                            onMonthSummary = onNavigateToMonthSummary,
                            onClick = { previewEntry = randomEntry }
                        )
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
    onRefresh: () -> Unit,
    isGenerating: Boolean = false,
    canGenerateWeek: Boolean = false,
    canGenerateMonth: Boolean = false,
    onWeekSummary: () -> Unit = {},
    onMonthSummary: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(520.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(28.dp)) {
            // 标题
            Text(
                "随机回顾",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(20.dp))

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
                        .heightIn(max = 180.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 弹性空间，把下方内容推到底部
            Spacer(modifier = Modifier.weight(1f))

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

            // 分割线
            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            Spacer(modifier = Modifier.height(12.dp))

            // 按钮行（小按钮，右对齐）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onWeekSummary
                    ) {
                        Text("本周", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(
                        onClick = onMonthSummary
                    ) {
                        Text("本月", style = MaterialTheme.typography.bodySmall)
                    }
                }

                TextButton(
                    onClick = onRefresh,
                    enabled = !isGenerating
                ) {
                    Icon(
                        painterResource(R.drawable.ic_refresh),
                        contentDescription = "再抽一条",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("再抽一条", style = MaterialTheme.typography.bodySmall)
                }
            }
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
