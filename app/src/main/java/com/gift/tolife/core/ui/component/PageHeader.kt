package com.gift.tolife.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gift.tolife.core.ui.theme.LifeType
import com.gift.tolife.core.ui.theme.PageGutter
import com.gift.tolife.core.ui.theme.ThemeExtended

/**
 * 页面大标题（2026-10-18）。
 *
 * 主页从标准 TopAppBar 的 20sp 标题升级为「眉题 + 28sp 版面标题」：
 * - 眉题是功能信息（日期 / 年月 / 品牌），不是装饰；
 * - 右侧动作与标题同一条基线上，不跟着眉题往下沉；
 * - 副页自动收成 20sp，并带一条发丝线，保持「翻页」的层级。
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    onBack: (() -> Unit)? = null,
    showDivider: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val isSubPage = onBack != null
    Column(modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (isSubPage) 6.dp else PageGutter,
                    end = 10.dp,
                    top = if (isSubPage) 4.dp else 8.dp,
                    bottom = if (isSubPage) 8.dp else 12.dp,
                ),
            verticalAlignment = Alignment.Top,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(2.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                if (eyebrow != null) {
                    Eyebrow(eyebrow)
                    Spacer(Modifier.height(5.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = if (isSubPage) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.headlineLarge
                        },
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        content = actions,
                    )
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = PageGutter),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            )
        }
    }
}

/** 眉题：一条 14dp 短痕 + 11sp 宽字距小字。全 App 的段落起点都用它。 */
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    withRule: Boolean = true,
) {
    val tint = color ?: ThemeExtended.current.accentText
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (withRule) {
            Box(
                Modifier
                    .width(14.dp)
                    .height(1.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tint.copy(alpha = 0.85f))
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = LifeType.eyebrow,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 安静的分组标题：比眉题更弱，不抢主色。 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(top = 8.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(12.dp)
                .height(1.5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.75f))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = LifeType.eyebrow.copy(letterSpacing = 0.9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 页面右上角的轻量动作：34dp 的浅色圆，外面保留 48dp 触控区。 */
@Composable
fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.size(44.dp)) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
