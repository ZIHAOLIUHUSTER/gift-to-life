package com.gift.tolife.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.gift.tolife.R
import com.gift.tolife.core.common.DateFormats
import com.gift.tolife.core.common.ImageUtil
import com.gift.tolife.core.common.ReviewDeepLink
import com.gift.tolife.core.database.EntryRepository
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.TagType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 随机回顾桌面小组件：展示一条随机记录（含缩略图），刷新按钮手动换一条。
 *
 * 纯手动刷新（updatePeriodMillis=0），无后台任务，零额外耗电。
 * 2026-10-18：跟随皮肤/深色；卡片改成纸面 + 发丝边 + 72dp 圆角相片；
 * 小尺寸时自动隐藏眉题/元信息/图片，只留正文和「换一条」。
 */
class RandomReviewWidget : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun repository(): EntryRepository
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        refresh(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        refresh(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH || intent.action == ACTION_RESTYLE) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RandomReviewWidget::class.java))
            refresh(context, manager, ids)
        } else {
            super.onReceive(context, intent)
        }
    }

    private fun refresh(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext, WidgetEntryPoint::class.java
                )
                val repository = entryPoint.repository()
                for (id in ids) {
                    val random = try {
                        repository.getRandomEntryWithTags()
                    } catch (t: Throwable) {
                        null
                    }
                    val options = manager.getAppWidgetOptions(id)
                    val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                        .takeIf { it > 0 } ?: 180
                    val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                        .takeIf { it > 0 } ?: 110
                    manager.updateAppWidget(id, buildViews(context, random, width, height, id))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun buildViews(
        context: Context,
        random: Pair<Entry, List<TagType>>?,
        widthDp: Int,
        heightDp: Int,
        appWidgetId: Int,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.random_review_widget)
        val palette = WidgetStyle.read(context)
        WidgetStyle.applyRandom(views, palette)

        // 点卡片打开 App（携带条目 ID，深链到该条目的详情预览）
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { openApp ->
            openApp.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            random?.let { openApp.putExtra(ReviewDeepLink.EXTRA_ENTRY_ID, it.first.id) }
            views.setOnClickPendingIntent(
                R.id.widget_container,
                PendingIntent.getActivity(
                    context,
                    REQUEST_OPEN + appWidgetId,
                    openApp,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        // 换一条：独立 PendingIntent，避免和卡片点击互相覆盖
        val refreshIntent = Intent(context, RandomReviewWidget::class.java).setAction(ACTION_REFRESH)
        views.setOnClickPendingIntent(
            R.id.widget_refresh,
            PendingIntent.getBroadcast(
                context,
                REQUEST_REFRESH + appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        // 尺寸自适应：小尺寸优先保住正文与换一条，大尺寸才露出眉题/元信息/照片。
        val compact = heightDp < 135
        val showLabel = heightDp >= 135
        val showMeta = heightDp >= 165
        val showImage = heightDp >= 175 && widthDp >= 220
        val textSizeSp = when {
            compact -> 13f
            heightDp < 190 -> 15f
            else -> 16f
        }
        val maxLines = when {
            heightDp < 135 -> 2
            heightDp < 165 -> 2
            heightDp < 190 -> 3
            heightDp < 220 -> 4
            else -> 5
        }

        views.setViewVisibility(R.id.widget_label_row, if (showLabel) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_meta, if (showMeta) View.VISIBLE else View.GONE)
        views.setTextViewTextSize(R.id.widget_content, TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        views.setInt(R.id.widget_content, "setMaxLines", maxLines)

        if (random == null) {
            views.setTextViewText(R.id.widget_content, "还没有记录\n去写下第一句")
            views.setTextColor(R.id.widget_content, palette.muted)
            views.setViewVisibility(R.id.widget_image, View.GONE)
            views.setViewVisibility(R.id.widget_footer, View.GONE)
            return views
        }

        val (entry, tags) = random
        val text = entry.content.ifBlank {
            entry.imageDescription?.takeIf { it.isNotBlank() } ?: "一张图片"
        }
        views.setTextViewText(R.id.widget_content, text.take(MAX_CONTENT_CHARS))
        views.setTextColor(R.id.widget_content, palette.ink)
        views.setViewVisibility(R.id.widget_footer, View.VISIBLE)

        if (showImage) {
            val raw = entry.imagePath?.takeIf { it.isNotBlank() }
                ?.let { ImageUtil.decodeThumbnail(it) }
            if (raw != null) {
                val rounded = WidgetStyle.roundedPhoto(raw, palette.border)
                views.setImageViewBitmap(R.id.widget_image, rounded)
                views.setViewVisibility(R.id.widget_image, View.VISIBLE)
                if (rounded !== raw) raw.recycle()
            } else {
                views.setViewVisibility(R.id.widget_image, View.GONE)
            }
        } else {
            views.setViewVisibility(R.id.widget_image, View.GONE)
        }

        val meta = buildString {
            append(DateFormats.formatDayTime(entry.createdAt))
            if (tags.isNotEmpty()) {
                append(" · ")
                append(tags.joinToString(" · ") { it.label })
            }
        }
        views.setTextViewText(R.id.widget_meta, meta)
        return views
    }

    companion object {
        const val ACTION_REFRESH = "com.gift.tolife.action.RANDOM_REVIEW_REFRESH"
        const val ACTION_RESTYLE = "com.gift.tolife.action.RANDOM_REVIEW_RESTYLE"
        private const val REQUEST_OPEN = 3001
        private const val REQUEST_REFRESH = 3002
        private const val MAX_CONTENT_CHARS = 160
    }
}
