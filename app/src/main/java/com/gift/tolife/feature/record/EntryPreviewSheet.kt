package com.gift.tolife.feature.record

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.gift.tolife.R
import com.gift.tolife.core.common.DateFormats
import com.gift.tolife.core.common.GallerySaver
import com.gift.tolife.core.common.ShareCardRenderer
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.TagType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EntryPreviewSheet(
    entry: Entry,
    tags: List<TagType>,
    onEdit: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onImageClick: (() -> Unit)? = null
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val saved = GallerySaver.saveImage(context, entry.imagePath!!, entry.createdAt)
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
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 时间 + 分享
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = DateFormats.formatDateTime(entry.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { shareEntry() }) {
                    Icon(
                        painterResource(R.drawable.ic_share),
                        contentDescription = "分享",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 全文（长按复制）
            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodyLarge,
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
                        .clip(RoundedCornerShape(8.dp))
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
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("移至回收站")
                }

                if (onEdit != null) {
                    Button(
                        onClick = {
                            onDismiss()
                            onEdit()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("编辑")
                    }
                }
            }
        }

        // 移至回收站确认对话框
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("移至回收站？") },
                text = { Text("这条记录可以稍后在设置 → 回收站中恢复。") },
                confirmButton = {
                    TextButton(onClick = {
                        onDismiss()
                        onDelete()
                        showDeleteConfirm = false
                    }) { Text("移至回收站") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
                }
            )
        }
    }
}