package com.gift.tolife.feature.record

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.gift.tolife.R
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.ui.UiTestTags
import com.gift.tolife.core.ui.component.AppEmptyState
import com.gift.tolife.core.model.EntryQuery
import com.gift.tolife.core.model.TagType
import com.gift.tolife.feature.record.EntryWithTags

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
                is RecordEvent.RequestComposerFocus -> composerFocusRequester.requestFocus()
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

    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.isSearchMode) {
                SearchTopBar(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                    onClose = viewModel::closeSearch,
                    focusRequester = focusRequester
                )
            } else {
                TopAppBar(
                    title = { Text("记录") },
                    actions = {
                        IconButton(onClick = viewModel::openSearch) {
                            Icon(painterResource(R.drawable.ic_search), contentDescription = "搜索")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isSearchMode) {
                FilterBar(
                    entryQuery = uiState.entryQuery,
                    onToggleTag = viewModel::toggleTagFilter,
                    onSetHasImage = viewModel::setFilterHasImage,
                    onClearFilters = viewModel::clearFilters
                )
            } else {
                EntryComposer(
                value = uiState.draftText,
                onValueChange = viewModel::setDraftText,
                pendingImageUri = uiState.pendingImageUri,
                isSaving = uiState.isSaving,
                onSave = viewModel::saveDraft,
                onPickImage = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onClearImage = viewModel::clearImage,
                focusRequester = composerFocusRequester
            )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(lazyItems.itemCount) { index ->
                    val item = lazyItems[index]
                    if (item != null) {
                        EntryCard(
                            entry = item.entry,
                            tags = item.tags,
                            onClick = {
                                previewEntry = item.entry to item.tags
                            },
                            onImageClick = { previewImagePath = item.entry.imagePath }
                        )
                    }
                }
                if (lazyItems.loadState.refresh is LoadState.NotLoading && lazyItems.itemCount == 0) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            AppEmptyState(
                                title = "没有找到记录",
                                description = "换个关键词或清除部分筛选条件"
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
        if (uiState.selectedEntry != null) {
            EditEntryBottomSheet(
                originalEntry = uiState.selectedEntry!!,
                originalTags = editTags.toSet(),
                onSave = { draft -> viewModel.saveEdit(draft) },
                onDismiss = viewModel::clearSelection,
                onPickImage = {
                    viewModel.setEditingImage(uiState.selectedEntry!!)
                },
                onImageClick = { previewImagePath = uiState.selectedEntry!!.imagePath }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    focusRequester: FocusRequester
) {
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag(UiTestTags.SEARCH_INPUT),
                placeholder = { Text("搜索记录...") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = "关闭搜索")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterBar(
    entryQuery: EntryQuery,
    onToggleTag: (TagType) -> Unit,
    onSetHasImage: (Boolean?) -> Unit,
    onClearFilters: () -> Unit
) {
    val tagTypes = TagType.entries

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 标签筛选行
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tagTypes.forEach { tag ->
                val selected = tag in entryQuery.selectedTags
                FilterChip(
                    selected = selected,
                    onClick = { onToggleTag(tag) },
                    label = { Text(tag.label, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 图片筛选（三个独立选项）
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(null to "全部", true to "有图", false to "无图").forEach { (value, label) ->
                FilterChip(
                    selected = entryQuery.hasImage == value,
                    onClick = { onSetHasImage(value) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onClearFilters) {
                Text("清除筛选", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
