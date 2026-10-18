package com.gift.tolife.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gift.tolife.core.ui.theme.LifeMotion
import com.gift.tolife.core.ui.theme.ThemeExtended

/**
 * 自定义底栏（2026-10-18）。
 *
 * 不再用 NavigationBar 的 12% 主色胶囊，改为：
 * - 21dp 细图标 + 10sp 标签；
 * - 选中只亮主色，并在标签下留一条 14×2dp 的短痕；
 * - 顶部一条发丝线，背景是白纸而不是纸底，让内容面自然收边。
 */
@Composable
fun AppBottomBar(
    selectedRoute: String?,
    onSelect: (Screen) -> Unit,
) {
    val accent = ThemeExtended.current.accentText
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            bottomNavItems.forEach { screen ->
                val selected = selectedRoute == screen.route
                val tint by animateColorAsState(
                    targetValue = if (selected) {
                        accent
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(LifeMotion.FAST),
                    label = "tabTint",
                )
                val mark by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    animationSpec = tween(LifeMotion.FAST),
                    label = "tabMark",
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .selectable(
                            selected = selected,
                            onClick = { onSelect(screen) },
                            role = Role.Tab,
                        )
                        .padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                        tint = tint,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = screen.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            letterSpacing = 0.4.sp,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        ),
                        color = tint,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .width(14.dp)
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(mark)
                    )
                }
            }
        }
    }
}
