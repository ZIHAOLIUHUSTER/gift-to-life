package com.gift.tolife.core.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 页面留白刻度（2026-09-27 v4.6.1 真机反馈）。
 *
 * 4.6.0 统一用了 16dp，真机上正文几乎顶到屏幕边、整页发闷：
 * 360dp 宽的机器上，16dp 留白意味着正文一行能塞 20+ 个汉字，视觉上没有呼吸。
 * 这里把页面留白提到 22dp，另外给「内容面内部」和「列表行上下」各定一个刻度，
 * 各页只引用这三个值，不再自己写数字。
 */

/** 页面左右留白（列表行、卡片、分组面统一用它）。 */
val PageGutter = 22.dp

/** 内容面（卡片 / 分组 / 弹层）内部左右留白。 */
val ContentPadding = 18.dp

/** 列表行上下留白。 */
val RowPaddingV = 16.dp
