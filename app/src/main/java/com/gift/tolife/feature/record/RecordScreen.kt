package com.gift.tolife.feature.record

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gift.tolife.core.model.EntryQuery
import com.gift.tolife.core.model.TagType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(viewModel: RecordViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var previewImagePath by remember { mutableStateOf<String?>(null) }

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
                    onClose = viewModel::toggleSearch
                )
            } else {
                TopAppBar(
                    title = { Text("记录") },
                    actions = {
                        IconButton(onClick = viewModel::toggleSearch) {
                            Icon(Icons.Filled.Search, contentDescription = "搜索")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        },
        floatingActionButton = {
            if (uiState.entries.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { /* 阶段 4: 随机回顾 */ },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Filled.Casino, contentDescription = "随机回顾")
                }
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
                    onClearImage = viewModel::clearImage
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(uiState.entries, key = { it.id }) { entry ->
                    EntryCard(
                        entry = entry,
                        onClick = { viewModel.selectEntry(entry) },
                        onImageClick = { previewImagePath = entry.imagePath }
                    )
                }
            }
        }

        // Edit bottom sheet
        if (uiState.selectedEntry != null) {
            EditEntryBottomSheet(
                entry = uiState.selectedEntry!!,
                onSave = viewModel::update,
                onDelete = viewModel::delete,
                onDismiss = viewModel::clearSelection,
                onRemoveImage = viewModel::removeImage,
                onReplaceImage = {
                    viewModel.setEditingImage(uiState.selectedEntry!!.id)
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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
                Icon(Icons.Filled.Close, contentDescription = "关闭搜索")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
