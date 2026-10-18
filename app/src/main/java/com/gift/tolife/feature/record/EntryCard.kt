package com.gift.tolife.feature.record

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gift.tolife.core.common.DateFormats
import com.gift.tolife.core.common.TextMatch
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.TagType
import com.gift.tolife.core.ui.theme.LifeType
import com.gift.tolife.core.ui.theme.PageGutter
import com.gift.tolife.core.ui.theme.RowPaddingV
import java.io.File

/** 记录正文；搜索模式下把命中字符标上主题色底色。 */
@Composable
private fun HighlightedContent(text: String, query: String, maxLines: Int) {
    val displayText = remember(text, query) { TextMatch.preview(text, query) }
    val ranges = remember(displayText, query) { TextMatch.ranges(displayText, query) }
    // 高亮只做提示：透明度压到 16%，不允许盖住文字本身的对比度
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val annotated = remember(displayText, ranges, highlight) {
        if (ranges.isEmpty()) {
            null
        } else {
            buildAnnotatedString {
                var cursor = 0
                ranges.forEach { range ->
                    if (range.first > cursor) append(displayText.substring(cursor, range.first))
                    withStyle(SpanStyle(background = highlight, fontWeight = FontWeight.Medium)) {
                        append(displayText.substring(range.first, range.last + 1))
                    }
                    cursor = range.last + 1
                }
                if (cursor < displayText.length) append(displayText.substring(cursor))
            }
        }
    }
    Text(
        text = annotated ?: AnnotatedString(displayText),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 记录列表的一行（2026-10-18 精修）。
 *
 * 仍是连续时间流（透明行 + 发丝线），但：
 * - 有图记录改为右侧 72dp 小相片，正文不再被通栏大图截断；
 * - 元信息只留时间与标签，日期交给上方的「今天 / 昨天」分组标题；
 * - 搜索态保留日期（`includeDate = true`），因为此时没有分组标题。
 */
@Composable
fun EntryCard(
    entry: Entry,
    tags: List<TagType> = emptyList(),
    query: String = "",
    onClick: () -> Unit,
    onImageClick: () -> Unit = {},
    includeDate: Boolean = false,
) {
    val hasImage = !entry.imagePath.isNullOrBlank()
    val meta = remember(entry.createdAt, tags, includeDate) {
        val timeText = if (includeDate) {
            DateFormats.formatDayTime(entry.createdAt)
        } else {
            DateFormats.formatTime(entry.createdAt)
        }
        (listOf(timeText) + tags.take(2).map { it.label }).joinToString(" · ")
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = PageGutter, vertical = RowPaddingV),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (entry.content.isBlank()) {
                    Text(
                        text = if (hasImage) "一张图片" else "空白记录",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    HighlightedContent(
                        text = entry.content,
                        query = query,
                        maxLines = if (hasImage) 3 else 4,
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = meta,
                    style = LifeType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (hasImage) {
                Spacer(Modifier.width(14.dp))
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(
                            0.75.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                            RoundedCornerShape(10.dp),
                        )
                        .clickable(onClick = onImageClick)
                        .padding(2.dp),
                ) {
                    AsyncImage(
                        model = File(entry.imagePath!!),
                        contentDescription = "记录图片",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = PageGutter),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        )
    }
}
