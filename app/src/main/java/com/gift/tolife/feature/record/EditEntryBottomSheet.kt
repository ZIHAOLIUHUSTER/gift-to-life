package com.gift.tolife.feature.record

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gift.tolife.core.common.DateFormats
import com.gift.tolife.core.ui.theme.LifeType
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.TagType
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditEntryBottomSheet(
    originalEntry: Entry,
    originalTags: Set<TagType>,
    onSave: (EntryEditDraft) -> Unit,
    onDismiss: () -> Unit,
    onImageClick: (() -> Unit)? = null,
    isSaving: Boolean = false
) {
    var draft by remember(originalEntry.id, originalTags) {
        mutableStateOf(EntryEditDraft.from(originalEntry, originalTags))
    }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var previewImageUri by remember(originalEntry.id) { mutableStateOf<android.net.Uri?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (!isSaving) uri?.let { draft = draft.copy(imageChange = ImageChange.Replace(it)) }
    }

    val keyboardController = LocalSoftwareKeyboardController.current

    val onDismissRequest: () -> Unit = {
        if (isSaving) {
            // Keep the submitted draft mounted until persistence completes.
        } else if (draft.isDirtyComparedWith(originalEntry, originalTags)) {
            showDiscardDialog = true
        } else {
            onDismiss()
        }
    }

    val currentSaving by rememberUpdatedState(isSaving)
    val currentDirty by rememberUpdatedState(draft.isDirtyComparedWith(originalEntry, originalTags))
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { next ->
            when {
                next != SheetValue.Hidden -> true
                currentSaving -> false
                currentDirty -> {
                    showDiscardDialog = true
                    false
                }
                else -> true
            }
        }
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
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
                .imePadding()
        ) {
            // 可滚动内容区
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp)
            ) {
                // 时间信息
                Text(
                    text = DateFormats.formatDateTime(originalEntry.createdAt),
                    style = LifeType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 正文在前，标签和图片管理不挡住写作路径。
                OutlinedTextField(
                    value = draft.content,
                    onValueChange = { draft = draft.copy(content = it) },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 300.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(6.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 标签可换行，适配放大字体。
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TagType.entries.forEach { tag ->
                        val selected = tag in draft.tags
                        FilterChip(
                            selected = selected,
                            enabled = !isSaving,
                            onClick = {
                                draft = draft.copy(
                                    tags = if (selected) draft.tags - tag else draft.tags + tag
                                )
                            },
                            label = { Text(tag.label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 图片区
                val imageChange = draft.imageChange
                val displayImagePath = when (imageChange) {
                    is ImageChange.Keep -> draft.originalImagePath
                    is ImageChange.Remove -> null
                    is ImageChange.Replace -> imageChange.uri.toString()
                }

                if (displayImagePath != null) {
                    val model: Any = if (imageChange is ImageChange.Replace) {
                        imageChange.uri
                    } else {
                        File(displayImagePath)
                    }

                    AsyncImage(
                        model = model,
                        contentDescription = "记录图片",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (imageChange is ImageChange.Replace) previewImageUri = imageChange.uri
                                else onImageClick?.invoke()
                            },
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 图片动作收敛为两个：更换 / 移除；看大图就是点图片本身
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                imagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !isSaving,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("更换")
                        }
                        TextButton(
                            onClick = { draft = draft.copy(imageChange = ImageChange.Remove) },
                            enabled = !isSaving,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("移除", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    TextButton(
                        onClick = {
                            imagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !isSaving,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("添加图片")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // 底部固定按钮行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        keyboardController?.hide()
                        onSave(draft.copy(content = draft.content.trim()))
                    },
                    enabled = !isSaving && EntrySavePolicy.canSave(
                        draft.content,
                        draft.imageChange != ImageChange.Remove &&
                            (draft.imageChange is ImageChange.Replace || draft.originalImagePath != null)
                    ),
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(if (isSaving) "保存中…" else "保存")
                }
            }
        }
    }

    previewImageUri?.let { uri ->
        ImagePreviewDialog(imageUri = uri, onDismiss = { previewImageUri = null })
    }

    // 放弃修改对话框
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("放弃修改") },
            text = { Text("您的修改尚未保存，确定放弃？") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onDismiss()
                }) { Text("放弃修改") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("继续编辑") }
            }
        )
    }
}