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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.gift.tolife.R
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.EntryQuery
import com.gift.tolife.core.model.TagType

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
    var showDeleteConfirm by remember { mutableStateOf<Entry?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val editingId = uiState.editingImageEntryId
            if (editingId != null) {
                val entry = uiState.entries.find { it.id == editingId }
                if (entry != null) {
                    viewModel.replaceImage(entry, it)
                }
            } else {
                viewModel.selectImage(it)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RecordEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.isSearchMode) {
                SearchTopBar(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                    onClose = viewModel::closeSearch
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
                    onToggleHasImage = {
                        val current = uiState.entryQuery.hasImage
                        viewModel.setFilterHasImage(
                            when (current) {
                                null -> true
                                true -> false
                                false -> null
                            }
                        )
                    },
                    onClearFilters = viewModel::clearFilters
                )
            } else {
                EntryComposer(
                    pendingImageUri = uiState.pendingImageUri,
                    onSave = viewModel::save,
                    onPickImage = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onClearImage = viewModel::clearImage,
                    initialText = uiState.pendingContentText
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(lazyItems.itemCount) { index ->
                    val entry = lazyItems[index]
                    if (entry != null) {
                        EntryCard(
                            entry = entry,
                            tags = uiState.entryTags[entry.id] ?: emptyList(),
                            onClick = {
                                previewEntry = entry to (uiState.entryTags[entry.id] ?: emptyList())
                            },
                            onImageClick = { previewImagePath = entry.imagePath }
                        )
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
                onDelete = { showDeleteConfirm = pEntry },
                onImageClick = { previewImagePath = pEntry.imagePath }
            )
        }

        // Edit bottom sheet
        if (uiState.selectedEntry != null) {
            EditEntryBottomSheet(
                entry = uiState.selectedEntry!!,
                onSave = { entry ->
                    viewModel.saveWithTags(entry, editTags)
                },
                onDismiss = {
                    viewModel.clearSelection()
                    viewModel.setEditTags(emptyList())
                },
                onRemoveImage = viewModel::removeImage,
                onReplaceImage = {
                    viewModel.setEditingImage(uiState.selectedEntry!!.id)
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onAddImage = {
                    viewModel.setEditingImage(uiState.selectedEntry!!.id)
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onImageClick = { previewImagePath = uiState.selectedEntry!!.imagePath },
                currentTags = editTags,
                onTagsChanged = { viewModel.setEditTags(it) }
            )
        }
    }

    // 删除确认弹窗
    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("删除记录") },
            text = { Text("删除后无法恢复，确定删除？") },
            confirmButton = {
                TextButton(onClick = {
                    val entry = showDeleteConfirm!!
                    viewModel.delete(entry)
                    showDeleteConfirm = null
                    previewEntry = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("取消")
                }
            }
        )
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
    onClose: () -> Unit
) {
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
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
    onToggleHasImage: () -> Unit,
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

        // 图片筛选 + 清除
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val hasImageLabel = when (entryQuery.hasImage) {
                null -> "全部"
                true -> "有图"
                false -> "无图"
            }
            FilterChip(
                selected = entryQuery.hasImage != null,
                onClick = onToggleHasImage,
                label = { Text(hasImageLabel, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.weight(1f))

            TextButton(onClick = onClearFilters) {
                Text("清除筛选", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
