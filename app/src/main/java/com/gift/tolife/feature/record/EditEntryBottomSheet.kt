package com.gift.tolife.feature.record

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.TagType
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEntryBottomSheet(
    originalEntry: Entry,
    originalTags: Set<TagType>,
    onSave: (EntryEditDraft) -> Unit,
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onImageClick: (() -> Unit)? = null
) {
    var draft by remember(originalEntry.id, originalTags) {
        mutableStateOf(EntryEditDraft.from(originalEntry, originalTags))
    }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { draft = draft.copy(imageChange = ImageChange.Replace(it)) }
    }

    val keyboardController = LocalSoftwareKeyboardController.current

    val onDismissRequest: () -> Unit = {
        if (draft.isDirtyComparedWith(originalEntry, originalTags)) {
            showDiscardDialog = true
        } else {
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
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
                    .padding(horizontal = 24.dp)
            ) {
                // 时间信息
                Text(
                    text = formatFullTime(originalEntry.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 标签选择
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TagType.entries.forEach { tag ->
                        val selected = tag in draft.tags
                        FilterChip(
                            selected = selected,
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
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = { onImageClick?.invoke() }) {
                            Text("查看大图")
                        }
                        TextButton(onClick = { draft = draft.copy(imageChange = ImageChange.Remove) }) {
                            Text("删除图片", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = {
                            onPickImage()
                            imagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }) {
                            Text("替换图片")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    TextButton(onClick = {
                        onPickImage()
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) {
                        Text("＋ 添加图片")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 编辑区
                OutlinedTextField(
                    value = draft.content,
                    onValueChange = { draft = draft.copy(content = it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 300.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("保存")
                }
            }
        }
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

private fun formatFullTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}