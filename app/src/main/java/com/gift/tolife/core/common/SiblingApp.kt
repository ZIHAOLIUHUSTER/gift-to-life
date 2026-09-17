package com.gift.tolife.core.common

import android.content.Context
import android.content.Intent

/**
 * To Life 系列兄弟 App 的互推入口（2026-09-17 品牌层统一）。
 *
 * 只做一件事：按包名尝试打开另一个 App；没装就返回 false，由调用方给提示。
 * 不查询对方数据、不做跨进程通信——保持两个 App 完全独立。
 */
object SiblingApp {

    /** Gift To Life（本 App）。 */
    const val GIFT_PACKAGE = "com.gift.tolife"

    /** Date To Life（课表日程）。 */
    const val DATE_PACKAGE = "com.lzh.datetolife"

    /** 是否已安装。 */
    fun isInstalled(context: Context, packageName: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(packageName) != null

    /** 打开同系列 App；未安装或启动失败返回 false。 */
    fun launch(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
