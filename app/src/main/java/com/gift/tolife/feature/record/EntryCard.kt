package com.gift.tolife.feature.record

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gift.tolife.core.common.DateFormats
import com.gift.tolife.core.common.TextMatch
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.TagType
import java.io.File

/** 记录正文；搜索模式下把命中字符标上主题色底色（2026-09-17）。 */
@Composable
private fun HighlightedContent(text: String, query: String, maxLines: Int) {
    val ranges = remember(text, query) { TextMatch.ranges(text, query) }
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val annotated = remember(text, ranges, highlight) {
        if (ranges.isEmpty()) {
            null
        } else {
            buildAnnotatedString {
                var cursor = 0
                ranges.forEach { range ->
                    if (range.first > cursor) append(text.substring(cursor, range.first))
                    withStyle(SpanStyle(background = highlight, fontWeight = FontWeight.Medium)) {
                        append(text.substring(range.first, range.last + 1))
                    }
                    cursor = range.last + 1
                }
                if (cursor < text.length) append(text.substring(cursor))
            }
        }
    }
    Text(
        text = annotated ?: AnnotatedString(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun EntryCard(
    entry: Entry,
    tags: List<TagType> = emptyList(),
    query: String = "",
    onClick: () -> Unit,
    onImageClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            // 图片缩略图
            if (!entry.imagePath.isNullOrBlank()) {
                AsyncImage(
                    model = File(entry.imagePath),
                    contentDescription = "记录图片",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 148.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .clickable(onClick = onImageClick),
                    contentScale = ContentScale.Crop
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(12.dp)
            ) {
                HighlightedContent(
                    text = entry.content,
                    query = query,
                    maxLines = 4,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 标签
                if (tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val visibleTags = tags.take(2)
                        val remaining = tags.size - 2
                        visibleTags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    tag.label,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (remaining > 0) {
                            Text(
                                "+$remaining",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = DateFormats.formatShortDateTime(entry.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
