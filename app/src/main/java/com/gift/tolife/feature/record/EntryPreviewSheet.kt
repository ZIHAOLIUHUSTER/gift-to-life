package com.gift.tolife.feature.record

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.gift.tolife.core.common.DateFormats
import com.gift.tolife.core.common.GallerySaver
import com.gift.tolife.core.common.ShareCardRenderer
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.TagType
import com.gift.tolife.core.ui.theme.LifeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryPreviewSheet(
    entry: Entry,
    tags: List<TagType>,
    onEdit: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onImageClick: (() -> Unit)? = null
) {
    var showMore by remember(entry.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val path = entry.imagePath ?: return@rememberLauncherForActivityResult
            val saved = GallerySaver.saveImage(context, path, entry.createdAt)
            if (saved) Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
            else Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "需要存储权限才能保存图片", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveImage() {
        val path = entry.imagePath ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val saved = GallerySaver.saveImage(context, path, entry.createdAt)
            if (saved) Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
            else Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
        } else {
            permissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    val scope = rememberCoroutineScope()

    fun shareEntry() {
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                ShareCardRenderer.render(entry.content, entry.createdAt, entry.imagePath)
            }
            if (bitmap == null) {
                Toast.makeText(context, "生成分享卡片失败", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val file = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "share").apply { mkdirs() }
                val f = File(dir, "gift_share_${System.currentTimeMillis()}.png")
                f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                f
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(Intent.createChooser(intent, "分享闪念")) }
                .onFailure { Toast.makeText(context, "没有可用的分享应用", Toast.LENGTH_SHORT).show() }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
                .padding(horizontal = 22.dp)
                .padding(bottom = 20.dp)
        ) {
            // 时间与按需操作固定在顶部，正文再长也不把编辑挤出屏幕。
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = DateFormats.formatDateTime(entry.createdAt),
                    style = LifeType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(onClick = { showMore = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                        DropdownMenuItem(
                            text = { Text("复制全文") },
                            onClick = {
                                showMore = false
                                clipboardManager.setText(AnnotatedString(entry.content))
                                Toast.makeText(context, "已复制全文", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("分享卡片") },
                            onClick = { showMore = false; shareEntry() }
                        )
                        if (!entry.imagePath.isNullOrBlank()) {
                            DropdownMenuItem(
                                text = { Text("保存原图") },
                                onClick = { showMore = false; saveImage() }
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // 全文（长按复制）
                Text(
                    text = entry.content,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 28.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = {
                            clipboardManager.setText(AnnotatedString(entry.content))
                            Toast.makeText(context, "已复制全文", Toast.LENGTH_SHORT).show()
                        }
                    )
                )

                // 图片（长按保存到相册）
                if (!entry.imagePath.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AsyncImage(
                        model = File(entry.imagePath),
                        contentDescription = "预览图片",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = { onImageClick?.invoke() },
                                onLongClick = { saveImage() }
                            ),
                        contentScale = ContentScale.Crop
                    )
                }

                // 标签
                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tags.forEach { tag ->
                            // 标签中性表达：不再用主色底小胶囊
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Text(
                                    tag.label,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )

            // 固定操作：软删除可撤销，不再二次确认。
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = {
                        onDelete()
                        onDismiss()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("移至回收站")
                }

                if (onEdit != null) {
                    Button(
                        onClick = {
                            onEdit()
                            onDismiss()
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("编辑")
                    }
                }
            }
        }
    }
}