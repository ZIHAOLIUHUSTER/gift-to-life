package com.gift.tolife.core.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// ============================================================
// 皮肤变体（To Life 系列 v1，与 Date To Life 同源）
// Gift 默认「青松」——它原本那支墨绿 `#5B7B6F` 正式纳入系列 token。
// ============================================================

enum class ThemeVariant(
    val displayName: String,
    val description: String,
) {
    WARM(
        displayName = "暖纸·赤陶",
        description = "暖白 · 纸感",
    ),
    COOL(
        displayName = "冷雾",
        description = "冷灰 · 清爽",
    ),
    ROSE(
        displayName = "烟粉·酒红",
        description = "灰粉 · 柔和",
    ),
    GREEN(
        displayName = "青松",
        description = "灰绿 · 系列主推",
    ),
    SLATE(
        displayName = "石墨蓝",
        description = "灰蓝 · 中性",
    ),
    LILAC(
        displayName = "藕荷紫",
        description = "浅紫 · 文静",
    ),
    ;

    companion object {
        /** Gift 默认皮肤：青松。 */
        val DEFAULT = GREEN

        fun fromName(name: String?): ThemeVariant =
            values().find { it.name == name } ?: DEFAULT
    }
}

/** 皮肤 → 调色板；[dark] = true 时先派生深色变体。 */
internal fun ThemeVariant.toPalette(dark: Boolean = false): MorandiPalette {
    val light = when (this) {
        ThemeVariant.WARM -> WarmPalette
        ThemeVariant.COOL -> CoolPalette
        ThemeVariant.ROSE -> RosePalette
        ThemeVariant.GREEN -> GreenPalette
        ThemeVariant.SLATE -> SlatePalette
        ThemeVariant.LILAC -> LilacPalette
    }
    return if (dark) light.toDark() else light
}

/** 由调色板构造 Material3 ColorScheme（浅/深共用一条映射）。 */
private fun schemeOf(p: MorandiPalette, dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        primary = p.primary,
        onPrimary = p.onPrimary,
        primaryContainer = p.primaryContainer,
        onPrimaryContainer = p.onPrimaryContainer,
        secondary = p.primary,
        onSecondary = p.onPrimary,
        secondaryContainer = p.secondaryContainer,
        onSecondaryContainer = p.onSecondaryContainer,
        tertiary = p.primary,
        onTertiary = p.onPrimary,
        tertiaryContainer = p.primaryContainer,
        onTertiaryContainer = p.onPrimaryContainer,
        background = p.background,
        onBackground = p.onBackground,
        surface = p.surface,
        onSurface = p.onSurface,
        surfaceVariant = p.surfaceVariant,
        onSurfaceVariant = p.onSurfaceVariant,
        surfaceTint = p.primary,
        surfaceDim = p.surfaceVariant,
        surfaceBright = p.surfaceContainer,
        surfaceContainerLowest = p.surfaceContainer,
        surfaceContainerLow = p.surfaceContainer,
        surfaceContainer = p.surfaceContainer,
        surfaceContainerHigh = p.surfaceContainerHigh,
        surfaceContainerHighest = p.surfaceContainerHigh,
        inverseSurface = p.onSurface,
        inverseOnSurface = p.surface,
        inversePrimary = p.primaryContainer,
        outline = p.outline,
        outlineVariant = p.outlineVariant,
        error = LifeStatus.DangerDark,
        onError = Color(0xFF0D1218),
        scrim = Color(0xFF000000),
    )
} else {
    lightColorScheme(
        primary = p.primary,
        onPrimary = p.onPrimary,
        primaryContainer = p.primaryContainer,
        onPrimaryContainer = p.onPrimaryContainer,
        secondary = p.primary,
        onSecondary = p.onPrimary,
        secondaryContainer = p.secondaryContainer,
        onSecondaryContainer = p.onSecondaryContainer,
        tertiary = p.primary,
        onTertiary = p.onPrimary,
        tertiaryContainer = p.primaryContainer,
        onTertiaryContainer = p.onPrimaryContainer,
        background = p.background,
        onBackground = p.onBackground,
        surface = p.surface,
        onSurface = p.onSurface,
        surfaceVariant = p.surfaceVariant,
        onSurfaceVariant = p.onSurfaceVariant,
        surfaceTint = p.primary,
        surfaceDim = p.surfaceVariant,
        surfaceBright = p.surfaceContainer,
        surfaceContainerLowest = p.surfaceContainer,
        surfaceContainerLow = p.surfaceContainer,
        surfaceContainer = p.surfaceContainer,
        surfaceContainerHigh = p.surfaceContainerHigh,
        surfaceContainerHighest = p.surfaceContainerHigh,
        inverseSurface = p.onSurface,
        inverseOnSurface = p.surface,
        inversePrimary = p.primaryContainer,
        outline = p.outline,
        outlineVariant = p.outlineVariant,
        error = LifeStatus.Danger,
        onError = Color(0xFFFFFFFF),
        scrim = Color(0xFF000000),
    )
}

internal fun ThemeVariant.toColorScheme(dark: Boolean = false) = schemeOf(toPalette(dark), dark)

// ============================================================
// 扩展色彩：业务代码不硬编码颜色
// ============================================================

data class ExtendedColors(
    /** 主题色带：大面积分区 / 空状态（随皮肤） */
    val zone: Color = GreenPalette.zone,
    /** 中性色带：第二档分区（取皮肤容器色，不抢色） */
    val zoneNeutral: Color = GreenPalette.surfaceContainer,
    /** 成功（AI 连通测试 ✓ 等） */
    val success: Color = LifeStatus.Success,
    /** 危险（失败、删除强调） */
    val danger: Color = LifeStatus.Danger,
    /**
     * 小字用的主色。浅色下是 primary 压暗后的版本（对纸底 ≥ 4.5:1），
     * 眉题、图标、链接等 11–13sp 文本一律用它，不再直接拿 primary 排小字。
     */
    val accentText: Color = GreenPalette.primary.remapLightness(0.32f, 1f),
    /** 发丝线：比分隔线更轻，用于卡片边缘和段落线。 */
    val hairline: Color = GreenPalette.outlineVariant,
    /** 当前是否深色；组件据此决定阴影/高光方向。 */
    val dark: Boolean = false,
)

private val LocalExtendedColors = staticCompositionLocalOf { ExtendedColors() }

private fun MorandiPalette.toExtended(dark: Boolean) = ExtendedColors(
    zone = zone,
    zoneNeutral = surfaceContainer,
    success = if (dark) LifeStatus.SuccessDark else LifeStatus.Success,
    danger = if (dark) LifeStatus.DangerDark else LifeStatus.Danger,
    accentText = if (dark) primary else primary.remapLightness(0.32f, 1f),
    hairline = outlineVariant,
    dark = dark,
)

/** 组件内通过 `ThemeExtended.current.zone` 访问。 */
val ThemeExtended: androidx.compose.runtime.ProvidableCompositionLocal<ExtendedColors>
    get() = LocalExtendedColors

// ============================================================
// 形状（To Life 系列 token v1）：input 6 / chip 8 / card 10 / action 12 / sheet 16
// ============================================================

val GiftShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

// ============================================================
// 主题入口
// ============================================================

@Composable
fun GiftTheme(
    variant: ThemeVariant = ThemeVariant.DEFAULT,
    themeMode: String = "system",
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val palette = variant.toPalette(darkTheme)
    val colorScheme = schemeOf(palette, darkTheme)
    val extended = palette.toExtended(darkTheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 边到边：状态栏/导航栏都交给页面纸底，图标只跟着深浅走。
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
            } else {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.surfaceContainer.toArgb()
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = GiftTypography,
            shapes = GiftShapes,
            content = content,
        )
    }
}
