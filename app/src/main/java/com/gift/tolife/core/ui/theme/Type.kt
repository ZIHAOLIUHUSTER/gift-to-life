package com.gift.tolife.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * To Life 系列字阶（2026-09-27 视觉重构，2026-10-18 精修）。
 *
 * 硬要求：项目实际会用到的档位**全部写满**，任何一档都不允许回落到 Material 默认值。
 *
 * 规则（见 docs/visual-refactor-spec.md §3.3）：
 * - 中文字距统一 0；
 * - 只有页面大标题用 Medium（500），其余最高 500，禁止 700；
 * - 正文不小于 13sp，正文优先可读性；
 * - 行高按中文习惯略比西文宽松。
 *
 * 2026-10-18 变化：主页大标题升到 28sp，display 档改为常规字重，
 * 让「记录 / 回忆 / 设置」从 AppBar 标题变成真正的版面标题。
 */
private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: Float = 0f,
) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

val GiftTypography = Typography(
    displayLarge = style(42, 50, FontWeight.Normal, -0.2f),
    displayMedium = style(38, 46, FontWeight.Normal, -0.2f),
    displaySmall = style(34, 42, FontWeight.Normal, -0.1f),

    headlineLarge = style(28, 36, FontWeight.Medium, 0.1f),
    headlineMedium = style(22, 30, FontWeight.Medium),
    headlineSmall = style(18, 26, FontWeight.Medium),

    titleLarge = style(20, 28, FontWeight.Medium),
    titleMedium = style(16, 24, FontWeight.Medium),
    titleSmall = style(14, 22, FontWeight.Medium),

    bodyLarge = style(16, 26),
    bodyMedium = style(14, 22),
    bodySmall = style(13, 19),

    labelLarge = style(14, 20, FontWeight.Medium),
    labelMedium = style(12, 18, FontWeight.Medium),
    labelSmall = style(11, 16),
)

/**
 * 版面级小字（不属于 Material 的 label 语义，但全 App 需要一致）。
 *
 * 这些样式只用于「日期、时间、计数、眉题」这类信息，
 * 不用它们排正文，也不允许再在页面里临时 copy 字号。
 */
object LifeType {
    /** 眉题：11sp、600、略宽字距；配一条 14dp 的短痕使用。 */
    val eyebrow = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.65.sp,
    )

    /** 元信息：时间、标签、计数；数字用等宽数位，纵向对齐更稳。 */
    val meta = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
        fontFeatureSettings = "tnum",
    )

    /** 引语：随机回顾卡的短句，行高给足，适合中文的呼吸。 */
    val quote = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 21.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.15.sp,
    )

    /** 仪表数字：设置页本月条数。 */
    val number = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        fontFeatureSettings = "tnum",
    )
}
