package com.gift.tolife.feature.record

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
    entry: Entry,
    onSave: (Entry) -> Unit,
    onDismiss: () -> Unit,
    onRemoveImage: ((Entry) -> Unit)? = null,
    onReplaceImage: (() -> Unit)? = null,
    onAddImage: (() -> Unit)? = null,
    onImageClick: (() -> Unit)? = null,
    currentTags: List<TagType> = emptyList(),
    onTagsChanged: ((List<TagType>) -> Unit)? = null,
) {
    var editedContent by remember(entry.id) { mutableStateOf(entry.content) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // 时间信息
            Text(
                text = formatFullTime(entry.createdAt),
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
                    val selected = tag in currentTags
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val updated = if (selected) currentTags - tag else currentTags + tag
                            onTagsChanged?.invoke(updated)
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

            // 已有图片显示
            if (!entry.imagePath.isNullOrBlank()) {
                AsyncImage(
                    model = File(entry.imagePath),
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
                    TextButton(onClick = { onRemoveImage?.invoke(entry) }) {
                        Text("删除图片", color = MaterialTheme.colorScheme.error)
                    }
                    if (onReplaceImage != null) {
                        TextButton(onClick = onReplaceImage) {
                            Text("替换图片")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            } else if (onAddImage != null) {
                TextButton(onClick = onAddImage) {
                    Text("＋ 添加图片")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 编辑区
            OutlinedTextField(
                value = editedContent,
                onValueChange = { editedContent = it },
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

            // 按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        onSave(entry.copy(content = editedContent.trim()))
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
}

private fun formatFullTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
