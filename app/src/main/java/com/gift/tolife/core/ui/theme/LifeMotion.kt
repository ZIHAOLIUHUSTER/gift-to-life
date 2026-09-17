package com.gift.tolife.core.ui.theme

/**
 * To Life 系列动效规范（2026-09-17）。
 *
 * 原则：无弹跳、无夸张缩放；动画只服务于"内容从哪来、到哪去"。
 * 组件里不要写裸数字，统一用这里的常量。
 */
object LifeMotion {
    /** 状态切换：选中、开关、颜色过渡。 */
    const val FAST = 150

    /** 内容替换：搜索/筛选结果、卡片内容切换、Crossfade。 */
    const val BASE = 200

    /** 弹层进出：BottomSheet / Dialog。 */
    const val SLOW = 250

    /** 列表增删：淡出 + 轻微位移。 */
    const val LIST = 200
}
