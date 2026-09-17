package com.gift.tolife.core.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// To Life 系列皮肤（2026-09-17 落地，与 Date To Life 0.18.0 同源）
//
// 6 套：暖纸·赤陶 / 冷雾(雾蓝) / 烟粉·酒红 / 青松 / 石墨蓝 / 藕荷紫
// Gift 默认「青松」——就是它原来那支墨绿，只是正式纳入系列 token。
//
// 每套包含：
//   - 中性骨架：background / surface / 两级容器 / outline / onSurface…
//   - 主色：primary / primaryContainer（按钮、选中态、输入聚焦）
//   - **主题色带 zone**：大面积分区与空状态用，色相跟主题，
//     饱和度按系列规则锁定（clamp(主色饱和×1.8, 26%, 38%)）、明度 87%
//
// Gift 的分类标签（闪念/事记/情绪/知识）按系列决定**不使用彩色**，保持中性 chip，
// 因此这里不引入系列那套 10 色分类板。
// ============================================================

/** 暖纸·赤陶 —— 暖白底 + 赤陶点睛，纸感、松弛。 */
/**
 * 暖纸·赤陶 —— 纸张基准皮肤。
 *
 * token v2（2026-09-17 纸张感修订）：**背景是纸、卡片是白纸、主色只做点睛**。
 * 旧版把主色相灌进整个中性骨架（背景灰绿、卡片比背景更暗），
 * 整页发闷、信息像被色块包住 —— 现在卡片一律比纸更亮。
 */
val WarmPalette = MorandiPalette(
    background = Color(0xFFF9F3E8),
    surface = Color(0xFFF9F3E8),
    surfaceVariant = Color(0xFFF1EBE1),
    surfaceContainer = Color(0xFFFFFCF7),
    surfaceContainerHigh = Color(0xFFF1EBE1),
    onBackground = Color(0xFF3A352E),
    onSurface = Color(0xFF3A352E),
    onSurfaceVariant = Color(0xFF6E675E),
    outline = Color(0xFFDBD3C7),
    outlineVariant = Color(0xFFEBE4D9),
    primary = Color(0xFFA2563E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF2E3DC),
    onPrimaryContainer = Color(0xFF6B3B2A),
    secondaryContainer = Color(0xFFF1EBE1),
    onSecondaryContainer = Color(0xFF3F3931),
    blockContent = Color(0xFF2F2C28),
    zone = Color(0xFFEAD7D1),
)

/** 冷雾 —— 冷灰蓝底 + 雾蓝点睛，高对比、清爽。 */
/**
 * 冷雾 —— 冷纸 + 雾蓝点睛，清爽；纸仍然很亮，只是偏冷。
 */
val CoolPalette = MorandiPalette(
    background = Color(0xFFF2F6FA),
    surface = Color(0xFFF2F6FA),
    surfaceVariant = Color(0xFFEAF0F5),
    surfaceContainer = Color(0xFFFCFEFF),
    surfaceContainerHigh = Color(0xFFEAF0F5),
    onBackground = Color(0xFF31383F),
    onSurface = Color(0xFF31383F),
    onSurfaceVariant = Color(0xFF636B74),
    outline = Color(0xFFCCD7DF),
    outlineVariant = Color(0xFFE1E9F0),
    primary = Color(0xFF396285),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDFE8F1),
    onPrimaryContainer = Color(0xFF2A4962),
    secondaryContainer = Color(0xFFEAF0F5),
    onSecondaryContainer = Color(0xFF333B42),
    blockContent = Color(0xFF2F2C28),
    zone = Color(0xFFD1DFEA),
)

/** 烟粉·酒红 —— 灰粉底 + 酒红点睛，柔和。 */
/**
 * 烟粉·酒红 —— 粉纸 + 酒红点睛。
 */
val RosePalette = MorandiPalette(
    background = Color(0xFFFAF3F2),
    surface = Color(0xFFFAF3F2),
    surfaceVariant = Color(0xFFF2EBEA),
    surfaceContainer = Color(0xFFFFFCFB),
    surfaceContainerHigh = Color(0xFFF2EBEA),
    onBackground = Color(0xFF3B3537),
    onSurface = Color(0xFF3B3537),
    onSurfaceVariant = Color(0xFF6E666A),
    outline = Color(0xFFDBD1D2),
    outlineVariant = Color(0xFFEBE3E4),
    primary = Color(0xFF8C4A52),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF0E2E4),
    onPrimaryContainer = Color(0xFF5E3239),
    secondaryContainer = Color(0xFFF2EBEA),
    onSecondaryContainer = Color(0xFF403A3C),
    blockContent = Color(0xFF2F2C28),
    zone = Color(0xFFEAD1D4),
)

/** 青松 —— Gift 默认。灰绿底 + 松绿点睛（Gift 原来的墨绿 `#5B7B6F`）。 */
/**
 * 青松 —— Gift 默认（就是它原来那支墨绿 `#5B7B6F`）。
 *
 * 旧版背景 `#F1F4F1` 是灰绿、卡片 `#D8DFD9` 更绿更暗 —— 这是"信息被绿色块包住"的根因。
 * 现在纸只留一丝暖绿，卡片是白纸，绿只在按钮 / 图标 / 色带上出现。
 */
val GreenPalette = MorandiPalette(
    background = Color(0xFFF5F7EF),
    surface = Color(0xFFF5F7EF),
    surfaceVariant = Color(0xFFEFF1E8),
    surfaceContainer = Color(0xFFFDFEFA),
    surfaceContainerHigh = Color(0xFFEFF1E8),
    onBackground = Color(0xFF363A32),
    onSurface = Color(0xFF363A32),
    onSurfaceVariant = Color(0xFF686E64),
    outline = Color(0xFFD3D8CB),
    outlineVariant = Color(0xFFE5E8DE),
    primary = Color(0xFF5B7B6F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2EAE6),
    onPrimaryContainer = Color(0xFF3F5347),
    secondaryContainer = Color(0xFFEFF1E8),
    onSecondaryContainer = Color(0xFF373B33),
    blockContent = Color(0xFF2F2C28),
    zone = Color(0xFFD5E7E0),
)

/** 石墨蓝 —— 中性灰蓝，最克制的一套。 */
/**
 * 石墨蓝 —— 中性灰纸 + 石墨蓝点睛，最克制的一套。
 */
val SlatePalette = MorandiPalette(
    background = Color(0xFFF4F6F7),
    surface = Color(0xFFF4F6F7),
    surfaceVariant = Color(0xFFECF0F2),
    surfaceContainer = Color(0xFFFCFDFE),
    surfaceContainerHigh = Color(0xFFECF0F2),
    onBackground = Color(0xFF333A3E),
    onSurface = Color(0xFF333A3E),
    onSurfaceVariant = Color(0xFF656C72),
    outline = Color(0xFFCFD6DB),
    outlineVariant = Color(0xFFE3E9ED),
    primary = Color(0xFF4C5A66),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE1E6EA),
    onPrimaryContainer = Color(0xFF3D474F),
    secondaryContainer = Color(0xFFECF0F2),
    onSecondaryContainer = Color(0xFF363C40),
    blockContent = Color(0xFF2F2C28),
    zone = Color(0xFFD5DFE7),
)

/** 藕荷紫 —— 浅紫底 + 藕荷点睛，文静。 */
/**
 * 藕荷紫 —— 紫纸 + 藕荷点睛，文静。
 */
val LilacPalette = MorandiPalette(
    background = Color(0xFFF9F6FA),
    surface = Color(0xFFF9F6FA),
    surfaceVariant = Color(0xFFF1EEF3),
    surfaceContainer = Color(0xFFFFFDFF),
    surfaceContainerHigh = Color(0xFFF1EEF3),
    onBackground = Color(0xFF38343B),
    onSurface = Color(0xFF38343B),
    onSurfaceVariant = Color(0xFF6C666F),
    outline = Color(0xFFD9D2DD),
    outlineVariant = Color(0xFFEAE5EC),
    primary = Color(0xFF705A78),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEAE3ED),
    onPrimaryContainer = Color(0xFF4E4055),
    secondaryContainer = Color(0xFFF1EEF3),
    onSecondaryContainer = Color(0xFF3A363C),
    blockContent = Color(0xFF2F2C28),
    zone = Color(0xFFE2D5E6),
)

// ============================================================
// 系列状态色（浅色 / 深色）
// 对比度：danger 6.81 / 7.27，success 5.41 / 9.76（WCAG AA 以上）
// ============================================================

object LifeStatus {
    val Danger = Color(0xFFAA2D00)
    val DangerDark = Color(0xFFE08A80)
    val Success = Color(0xFF0F7A43)
    val SuccessDark = Color(0xFF5FCF9E)
    val Warning = Color(0xFFA05C00)
    val WarningDark = Color(0xFFE8B063)
    val Info = Color(0xFF1B61C9)
    val InfoDark = Color(0xFF7FB2FF)
}

// ============================================================
// 深色变体：不手写 6×19 个色号，由浅色皮肤按固定 HSL 规则派生
// （与 Date To Life 同一套规则；对比度由 LifeThemeTest 按 WCAG 验收）
// ============================================================

/** 一套皮肤的全部颜色（浅色/深色共用同一结构）。 */
data class MorandiPalette(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val blockContent: Color,
    /** 主题色带：大面积分区 / 空状态 / 卡片强调（To Life 系列 token v1）。 */
    val zone: Color,
)

/** RGB(0..1) → HSL(0..1)。纯 Kotlin，不依赖 android.graphics —— 保证可单测。 */
private fun toHsl(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val lightness = (max + min) / 2f
    if (max == min) return Triple(0f, 0f, lightness)
    val d = max - min
    val saturation = if (lightness > 0.5f) d / (2f - max - min) else d / (max + min)
    val hue = when (max) {
        r -> ((g - b) / d + if (g < b) 6f else 0f)
        g -> ((b - r) / d + 2f)
        else -> ((r - g) / d + 4f)
    } / 6f
    return Triple(hue, saturation, lightness)
}

private fun hueToRgb(p: Float, q: Float, t0: Float): Float {
    var t = t0
    if (t < 0f) t += 1f
    if (t > 1f) t -= 1f
    return when {
        t < 1f / 6f -> p + (q - p) * 6f * t
        t < 1f / 2f -> q
        t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
        else -> p
    }
}

/** HSL(0..1) → RGB(0..1)。 */
private fun fromHsl(h: Float, s: Float, l: Float): Triple<Float, Float, Float> {
    if (s == 0f) return Triple(l, l, l)
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    return Triple(
        hueToRgb(p, q, h + 1f / 3f),
        hueToRgb(p, q, h),
        hueToRgb(p, q, h - 1f / 3f),
    )
}

/** 把颜色重映射到指定明度，并按比例缩放饱和度（深底压饱和，避免又浓又脏）。 */
internal fun Color.remapLightness(targetLightness: Float, saturationScale: Float): Color {
    val (h, s, _) = toHsl(red, green, blue)
    val (r, g, b) = fromHsl(h, (s * saturationScale).coerceIn(0f, 1f), targetLightness)
    return Color(r, g, b, alpha)
}

/** 由浅色皮肤派生深色皮肤。 */
internal fun MorandiPalette.toDark(): MorandiPalette = MorandiPalette(
    background = background.remapLightness(0.13f, 0.40f),
    surface = surface.remapLightness(0.13f, 0.40f),
    surfaceVariant = surfaceVariant.remapLightness(0.29f, 0.40f),
    surfaceContainer = surfaceContainer.remapLightness(0.19f, 0.40f),
    surfaceContainerHigh = surfaceContainerHigh.remapLightness(0.25f, 0.40f),
    onBackground = onBackground.remapLightness(0.89f, 0.50f),
    onSurface = onSurface.remapLightness(0.89f, 0.50f),
    onSurfaceVariant = onSurfaceVariant.remapLightness(0.70f, 0.50f),
    outline = outline.remapLightness(0.50f, 0.60f),
    outlineVariant = outlineVariant.remapLightness(0.38f, 0.60f),
    primary = primary.remapLightness(0.72f, 1.00f),
    onPrimary = onPrimary.remapLightness(0.14f, 1.00f),
    primaryContainer = primaryContainer.remapLightness(0.28f, 0.40f),
    onPrimaryContainer = onPrimaryContainer.remapLightness(0.87f, 0.50f),
    secondaryContainer = secondaryContainer.remapLightness(0.23f, 0.40f),
    onSecondaryContainer = onSecondaryContainer.remapLightness(0.89f, 0.50f),
    blockContent = blockContent,
    zone = zone.remapLightness(0.20f, 0.75f),
)
