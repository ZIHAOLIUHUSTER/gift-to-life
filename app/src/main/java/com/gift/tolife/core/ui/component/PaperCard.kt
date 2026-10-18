package com.gift.tolife.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gift.tolife.core.ui.theme.ThemeExtended

/**
 * 全 App 的内容面（2026-10-18 精修）。
 *
 * 与旧版 Card 的区别：
 * - 边框更细（0.75dp，且默认带透明度），不再每张卡都像线框图；
 * - 需要「浮起来」的地方用极轻的投影（首页书写案、随机回顾卡），
 *   分组、列表容器仍然保持 0 阴影；
 * - 深色下边框更轻、投影交给系统，不额外加白光。
 */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    elevation: Dp = 0.dp,
    borderAlpha: Float = 0.55f,
    borderColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val dark = ThemeExtended.current.dark
    val edge = borderColor ?: MaterialTheme.colorScheme.outlineVariant.copy(
        alpha = if (dark) borderAlpha * 0.75f else borderAlpha
    )
    val shadowColor = if (dark) Color.Black.copy(alpha = 0.45f) else Color(0xFF30362E).copy(alpha = 0.16f)
    // 阴影必须放在调用方 modifier 的外层：卡片常带 clip(shape) 截住水波纹，
    // 若阴影后置会被一起裁掉，圆角外侧就没有光了。
    val withShadow = if (elevation > 0.dp) {
        Modifier.shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = shadowColor,
            spotColor = shadowColor,
        ).then(modifier)
    } else {
        modifier
    }

    Surface(
        modifier = withShadow,
        shape = shape,
        color = color,
        border = BorderStroke(0.75.dp, edge),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        content = content,
    )
}

/**
 * 卡片按压时的轻微回弹：0.985 → 1，无弹跳。
 * 只给「整张卡可点」的地方用；普通列表行保留水波纹即可。
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.985f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 700f),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
