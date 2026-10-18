package com.gift.tolife.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gift.tolife.core.ui.theme.MorandiPalette
import com.gift.tolife.core.ui.theme.ThemeVariant
import com.gift.tolife.core.ui.theme.toPalette

/**
 * 皮肤卡片（对齐 Date To Life 的 `ThemeOptionCard`，P42 2026-09-21）。
 *
 * 旧版（P39）是一行一张全宽列表卡：32dp 小色块 + 右侧 RadioButton，
 * 六套皮肤排下来比屏幕还高，末项与深色模式选项被底栏裁掉（用户截图反馈）。
 * 现在改成 3×2 网格的**迷你页面预览**：56dp 纸卡 + 名字，
 * 选中时描主色边、名字转主色 —— 与 Date 的观感一致。
 *
 * 预览层级 = 纸（外）→ 白卡（内）→ 墨（文字线），主色只出现一次（顶部点睛条）。
 */
@Composable
fun SkinOptionCard(
    variant: ThemeVariant,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 当前是否为深色模式：色卡必须展示用户实际会看到的配色。 */
    darkTheme: Boolean = false,
) {
    val palette = variant.toPalette(darkTheme)
    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(shape)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                    },
                    shape = shape,
                )
                .background(palette.background)
                .padding(7.dp),
        ) {
            SkinPreviewContent(palette = palette)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = variant.displayName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 色卡内的迷你页面：一小段点睛色 + 一张白卡 + 两条中性文字线。
 * 与 Date To Life 的 `ThemePreviewContent` 同构，保证两个 App 的换肤预览一致。
 */
@Composable
private fun SkinPreviewContent(
    palette: MorandiPalette,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(
            Modifier
                .width(18.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(palette.primary),
        )

        Spacer(Modifier.height(4.dp))

        Column(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
                .background(palette.surfaceContainer)
                .padding(5.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.85f)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(palette.onSurfaceVariant.copy(alpha = 0.4f)),
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.55f)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(palette.onSurfaceVariant.copy(alpha = 0.28f)),
            )
        }
    }
}
