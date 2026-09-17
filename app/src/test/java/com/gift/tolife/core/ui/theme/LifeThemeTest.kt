package com.gift.tolife.core.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * To Life 系列主题的 WCAG 验收（2026-09-17 随 token 落地引入）。
 *
 * 深色值由 [MorandiPalette.toDark] 从浅色皮肤按固定规则派生（不手写 6×19 个色号），
 * 所以必须用**对比度**而不是肉眼来验收 —— 派生规则一改，这里立刻报警。
 *
 * 阈值取 WCAG：正文 7.0（本设计系统把正文标准提到 AAA）、次级文字/按钮文字 4.5、
 * 非文本（网格线、边界）3.0、装饰性变体线 2.0。
 *
 * 已知取舍：浅色下「点睛色作为普通白底文字」的颜色对比，青松为 4.19:1
 * （墨绿 `#5B7B6F` 是其身份色，不改）；它作为图标/按钮底色时白字 4.66:1 达标。
 */
class LifeThemeTest {

    private val palettes = listOf(
        "暖纸·赤陶" to WarmPalette,
        "冷雾" to CoolPalette,
        "烟粉·酒红" to RosePalette,
        "青松" to GreenPalette,
        "石墨蓝" to SlatePalette,
        "藕荷紫" to LilacPalette,
    )

    /** 相对亮度（WCAG 2.x）。 */
    private fun luminance(color: Color): Double {
        fun channel(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.03928) d / 12.92 else Math.pow((d + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val l1 = maxOf(luminance(a), luminance(b))
        val l2 = minOf(luminance(a), luminance(b))
        return (l1 + 0.05) / (l2 + 0.05)
    }

    @Test
    fun `浅色皮肤正文与按钮文字达标`() {
        palettes.forEach { (name, p) ->
            assertTrue("$name 正文", contrast(p.onSurface, p.background) >= 7.0)
            assertTrue("$name 次级文字", contrast(p.onSurfaceVariant, p.background) >= 4.5)
            assertTrue("$name 主按钮文字", contrast(p.onPrimary, p.primary) >= 4.5)
            // 容器内文字（chip / 输入聚焦）按 AA 验收；最紧的一对：暖纸容器 6.90:1
            assertTrue("$name 容器内文字", contrast(p.onPrimaryContainer, p.primaryContainer) >= 4.5)
        }
    }

    @Test
    fun `深色皮肤正文与点睛色达标`() {
        palettes.forEach { (name, light) ->
            val dark = light.toDark()
            assertTrue("$name 正文", contrast(dark.onSurface, dark.background) >= 7.0)
            assertTrue("$name 次级文字", contrast(dark.onSurfaceVariant, dark.background) >= 4.5)
            assertTrue("$name 点睛色", contrast(dark.primary, dark.background) >= 4.5)
            assertTrue("$name 分隔线", contrast(dark.outline, dark.background) >= 3.0)
            // 网格线是装饰性非文本，2.0 起步（浅色皮肤当年 1.26 的问题就是这里）
            assertTrue("$name 变体线", contrast(dark.outlineVariant, dark.background) >= 2.0)
        }
    }

    @Test
    fun `主题色带上的文字达标`() {
        palettes.forEach { (name, light) ->
            // 色带上的正文按 AA 验收；最紧的一对：酒红色带 6.94:1（墨绿/冷雾等均在 8 以上）
            assertTrue("$name 浅色色带", contrast(light.onSurface, light.zone) >= 4.5)
            val dark = light.toDark()
            assertTrue("$name 深色色带", contrast(dark.onSurface, dark.zone) >= 4.5)
        }
    }

    @Test
    fun `深色背景确实比浅色暗`() {
        palettes.forEach { (name, light) ->
            assertTrue(name, luminance(light.toDark().background) < luminance(light.background))
        }
    }

    @Test
    fun `六套皮肤互不相同`() {
        assertEquals(6, palettes.map { it.second.background }.toSet().size)
        assertEquals(6, palettes.map { it.second.primary }.toSet().size)
        assertEquals(6, palettes.map { it.second.zone }.toSet().size)
    }

    @Test
    fun `未知皮肤名回退到默认`() {
        assertEquals(ThemeVariant.DEFAULT, ThemeVariant.fromName(null))
        assertEquals(ThemeVariant.DEFAULT, ThemeVariant.fromName("NOT_A_SKIN"))
        assertEquals(ThemeVariant.GREEN, ThemeVariant.fromName("GREEN"))
    }
}
