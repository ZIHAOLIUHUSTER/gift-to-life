package com.gift.tolife.core.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gift.tolife.core.ui.theme.ThemeExtended

/**
 * 空状态（2026-10-18 精修）。
 *
 * 一枚 38dp 的细环 + 一点星形，像纸上盖的一个小印记；
 * 不再用实心主色圆，也不套第二层卡片。
 */
@Composable
fun AppEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val accent = ThemeExtended.current.accentText
    Column(
        modifier = modifier.padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .border(1.dp, accent.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "✦",
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, lineHeight = 16.sp),
                color = accent.copy(alpha = 0.72f),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(6.dp))
            TextButton(
                onClick = onAction,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(actionLabel) }
        }
    }
}
