package com.gift.tolife.feature.record

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.EntryQuery
import com.gift.tolife.core.model.TagType
import com.gift.tolife.core.ui.UiTestTags
import com.gift.tolife.core.ui.component.AppEmptyState
import com.gift.tolife.core.ui.component.HeaderIconButton
import com.gift.tolife.core.ui.component.PageHeader
import com.gift.tolife.core.ui.theme.LifeType
import com.gift.tolife.core.ui.theme.PageGutter
import com.gift.tolife.core.ui.theme.ThemeExtended
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    viewModel: RecordViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val editTags by viewModel.editTags.collectAsState()
    val lazyItems = viewModel.entriesPagingData.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }
    var previewImagePath by remember { mutableStateOf<String?>(null) }
    var previewEntry by remember { mutableStateOf<Pair<Entry, List<TagType>>?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val composerFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val editingEntry = uiState.editingImageEntry
            if (editingEntry != null) {
                viewModel.replaceImage(editingEntry, it)
            } else {
                viewModel.selectImage(it)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RecordEvent.EntrySaved -> snackbarHostState.showSnackbar("已记录")
                is RecordEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                is RecordEvent.EntryMovedToRecycleBin -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "已移至回收站",
                        actionLabel = "撤销",
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.restoreEntry(event.entryId)
                    }
                }
            }
        }
    }

    LaunchedEffect(uiState.isSearchMode) {
        if (uiState.isSearchMode) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(
        uiState.composerFocusRequest, uiState.isSearchMode, uiState.selectedEntry,
        previewEntry, previewImagePath, showFilterSheet
    ) {
        val request = uiState.composerFocusRequest
        if (request != null && !uiState.isSearchMode && uiState.selectedEntry == null &&
            previewEntry == null && previewImagePath == null && !showFilterSheet
        ) {
            composerFocusRequester.requestFocus()
            keyboardController?.show()
            viewModel.consumeComposerFocus(request)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    val activeFilterCount = uiState.entryQuery.selectedTags.size +
        (if (uiState.entryQuery.hasImage != null) 1 else 0)
    val filterSummary = buildFilterSummary(uiState.entryQuery)

    val todayEyebrow = remember {
        val today = LocalDate.now()
        "${today.monthValue}月${today.dayOfMonth}日 · ${weekName(today.dayOfWeek)}"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (uiState.isSearchMode) {
                SearchTopBar(
                    query = uiState.searchQuery,
                    filterActive = activeFilterCount > 0,
                    onQueryChange = viewModel::setSearchQuery,
                    onClose = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        viewModel.closeSearch()
                    },
                    onOpenFilter = { showFilterSheet = true },
                    focusRequester = focusRequester
                )
            } else {
                PageHeader(
                    title = "记录",
                    eyebrow = todayEyebrow,
                    actions = {
                        HeaderIconButton(
                            icon = Icons.Rounded.Search,
                            contentDescription = "搜索",
                            onClick = viewModel::openSearch,
                        )
                    },
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!uiState.isSearchMode) {
                uiState.pendingShares.firstOrNull()?.let { pending ->
                    PendingShareNotice(
                        pending = pending,
                        count = uiState.pendingShares.size,
                        hasDraft = EntryInputParser.parse(uiState.draftText).canSave(uiState.pendingImageUri != null),
                        isSaving = uiState.isSaving,
                        onReceive = { viewModel.saveDraftAndReceive(pending.id) },
                        onKeepDraft = { viewModel.keepDraft(pending.id) }
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageGutter, vertical = 10.dp)
                ) {
                    EntryComposer(
                        value = uiState.draftText,
                        onValueChange = viewModel::setDraftText,
                        pendingImageUri = uiState.pendingImageUri,
                        isSaving = uiState.isSaving,
                        onSave = viewModel::saveDraft,
                        onPickImage = {
                            imagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onClearImage = viewModel::clearImage,
                        focusRequester = composerFocusRequester
                    )
                }
            } else if (activeFilterCount > 0) {
                // 有筛选时才出现的一行摘要，可一键清除（旧版是 7 个常驻 chip）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = PageGutter, end = 10.dp, top = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = filterSummary,
                        style = LifeType.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = viewModel::clearFilters, modifier = Modifier.heightIn(min = 44.dp)) {
                        Text(
                            "清除",
                            style = MaterialTheme.typography.labelMedium,
                            color = ThemeExtended.current.accentText,
                        )
                    }
                }
            }

            if (uiState.isSearchMode && uiState.searchQuery.isNotBlank()) {
                Text(
                    text = "找到 ${lazyItems.itemCount} 条",
                    style = LifeType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = PageGutter,
                        end = PageGutter,
                        top = if (activeFilterCount > 0) 0.dp else 2.dp,
                        bottom = 2.dp,
                    ),
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp)
            ) {
                items(
                    count = lazyItems.itemCount,
                    key = { index -> lazyItems[index]?.entry?.id ?: index }
                ) { index ->
                    val item = lazyItems[index]
                    if (item != null) {
                        val previous = if (index > 0) lazyItems[index - 1] else null
                        val showDayHeader = !uiState.isSearchMode &&
                            !isSameDay(previous?.entry?.createdAt, item.entry.createdAt)
                        Column {
                            if (showDayHeader) {
                                DayHeader(timestamp = item.entry.createdAt)
                            }
                            EntryCard(
                                entry = item.entry,
                                tags = item.tags,
                                query = uiState.entryQuery.searchText,
                                onClick = { previewEntry = item.entry to item.tags },
                                onImageClick = { previewImagePath = item.entry.imagePath },
                                includeDate = uiState.isSearchMode,
                            )
                        }
                    }
                }
                if (lazyItems.loadState.refresh is LoadState.NotLoading && lazyItems.itemCount == 0) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AppEmptyState(
                                title = if (uiState.isSearchMode) "没有找到记录" else "还没有记录",
                                description = if (uiState.isSearchMode) {
                                    "换个关键词或调整筛选"
                                } else {
                                    "写下第一句，它会留在这里"
                                },
                            )
                        }
                    }
                }
            }
        }

        // 预览弹层
        if (previewEntry != null) {
            val (pEntry, pTags) = previewEntry!!
            EntryPreviewSheet(
                entry = pEntry,
                tags = pTags,
                onEdit = {
                    viewModel.selectEntry(pEntry)
                    viewModel.loadTags(pEntry.id)
                    previewEntry = null
                },
                onDismiss = { previewEntry = null },
                onDelete = { viewModel.delete(pEntry) },
                onImageClick = { previewImagePath = pEntry.imagePath }
            )
        }

        // Edit bottom sheet
        val editingEntry = uiState.selectedEntry
        if (editingEntry != null && uiState.editingTagsEntryId == editingEntry.id) {
            EditEntryBottomSheet(
                originalEntry = editingEntry,
                originalTags = editTags.toSet(),
                onSave = { draft -> viewModel.saveEdit(draft) },
                onDismiss = viewModel::clearSelection,
                onImageClick = { previewImagePath = editingEntry.imagePath },
                isSaving = uiState.isSavingEdit
            )
        }
    }

    // 图片预览 Dialog
    if (previewImagePath != null) {
        ImagePreviewDialog(
            imagePath = previewImagePath!!,
            onDismiss = { previewImagePath = null }
        )
    }

    // 筛选面板：关闭即生效，面板内不再堆确认按钮
    if (showFilterSheet) {
        FilterSheet(
            entryQuery = uiState.entryQuery,
            resultCount = lazyItems.itemCount,
            onToggleTag = viewModel::toggleTagFilter,
            onSetHasImage = viewModel::setFilterHasImage,
            onClearFilters = viewModel::clearFilters,
            onDismiss = { showFilterSheet = false }
        )
    }
}

@Composable
private fun PendingShareNotice(
    pending: PendingShare,
    count: Int,
    hasDraft: Boolean,
    isSaving: Boolean,
    onReceive: () -> Unit,
    onKeepDraft: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = PageGutter)) {
        Text(
            text = if (count > 1) "待接收分享 · $count 条" else "待接收分享",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = pending.content.text?.takeIf { it.isNotBlank() } ?: "图片",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onReceive, enabled = !isSaving, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (hasDraft) "先保存再接收" else "接收分享")
            }
            TextButton(onClick = onKeepDraft, enabled = !isSaving, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("保留草稿")
            }
        }
    }
}

private fun buildFilterSummary(query: EntryQuery): String {
    val parts = query.selectedTags.map { it.label }.toMutableList()
    when (query.hasImage) {
        true -> parts += "有图"
        false -> parts += "无图"
        null -> Unit
    }
    return parts.joinToString(" · ")
}

private fun weekName(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}

private fun localDateOf(timestamp: Long): LocalDate =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()

private fun isSameDay(previous: Long?, current: Long): Boolean {
    if (previous == null) return false
    return localDateOf(previous) == localDateOf(current)
}

/** 记录流里的日期分隔：今天/昨天用主色，其余日期退到次级。 */
@Composable
private fun DayHeader(timestamp: Long) {
    val date = localDateOf(timestamp)
    val today = LocalDate.now()
    val label = when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> "${date.monthValue}月${date.dayOfMonth}日"
    }
    val detail = if (date == today || date == today.minusDays(1)) {
        "${date.monthValue}月${date.dayOfMonth}日 · ${weekName(date.dayOfWeek)}"
    } else {
        weekName(date.dayOfWeek)
    }
    val accent = ThemeExtended.current.accentText

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PageGutter, end = PageGutter, top = 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = LifeType.eyebrow,
            color = accent,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = detail,
            style = LifeType.eyebrow.copy(letterSpacing = 0.35.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun SearchTopBar(
    query: String,
    filterActive: Boolean,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onOpenFilter: () -> Unit,
    focusRequester: FocusRequester
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        },
        animationSpec = tween(150),
        label = "searchBorder",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "关闭搜索",
                modifier = Modifier.size(20.dp),
            )
        }
        Surface(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 46.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, borderColor),
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 46.dp)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused }
                        .testTag(UiTestTags.SEARCH_INPUT),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    text = "搜索记录与想法",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.66f),
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "清空",
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            IconButton(onClick = onOpenFilter, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Rounded.FilterList,
                    contentDescription = "筛选",
                    modifier = Modifier.size(20.dp),
                )
            }
            if (filterActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(ThemeExtended.current.accentText)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    entryQuery: EntryQuery,
    resultCount: Int,
    onToggleTag: (TagType) -> Unit,
    onSetHasImage: (Boolean?) -> Unit,
    onClearFilters: () -> Unit,
    onDismiss: () -> Unit
) {
    val tagTypes = TagType.entries
    val hasFilter = entryQuery.selectedTags.isNotEmpty() || entryQuery.hasImage != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                width = 36.dp,
                height = 4.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, bottom = 32.dp)
        ) {
            Text("筛选", style = MaterialTheme.typography.titleLarge)
            HorizontalDivider(
                modifier = Modifier.padding(top = 14.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )

            Spacer(Modifier.height(18.dp))
            Text(
                "标签",
                style = LifeType.eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tagTypes.forEach { tag ->
                    val selected = tag in entryQuery.selectedTags
                    FilterChip(
                        selected = selected,
                        onClick = { onToggleTag(tag) },
                        label = { Text(tag.label) },
                        shape = MaterialTheme.shapes.small,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "图片",
                style = LifeType.eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "全部", true to "有图", false to "无图").forEach { (value, label) ->
                    val selected = entryQuery.hasImage == value
                    FilterChip(
                        selected = selected,
                        onClick = { onSetHasImage(value) },
                        label = { Text(label) },
                        shape = MaterialTheme.shapes.small,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$resultCount 条记录符合条件",
                    style = LifeType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (hasFilter) {
                    TextButton(
                        onClick = onClearFilters,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("重置", color = ThemeExtended.current.accentText)
                    }
                }
            }
        }
    }
}
