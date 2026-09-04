package com.gift.tolife.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
 * 随机回顾桌面小组件：展示一条随机记录（含缩略图），骰子按钮手动换一条。
 * 纯手动刷新（updatePeriodMillis=0），无后台任务，零额外耗电。
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
        refreshAll(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RandomReviewWidget::class.java))
            refreshAll(context, manager, ids)
        } else {
            super.onReceive(context, intent)
        }
    }

    private fun refreshAll(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext, WidgetEntryPoint::class.java
                )
                val repository = entryPoint.repository()
                // 每个实例独立随机一条
                for (id in ids) {
                    val random = try {
                        repository.getRandomEntryWithTags()
                    } catch (t: Throwable) {
                        null
                    }
                    manager.updateAppWidget(id, buildViews(context, random))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun buildViews(context: Context, random: Pair<Entry, List<TagType>>?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.random_review_widget)

        // 点卡片打开 App（携带条目 ID，深链到该条目的详情预览）
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { openApp ->
            openApp.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            random?.let { openApp.putExtra(ReviewDeepLink.EXTRA_ENTRY_ID, it.first.id) }
            views.setOnClickPendingIntent(
                R.id.widget_container,
                PendingIntent.getActivity(
                    context, REQUEST_OPEN, openApp,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
        // 骰子按钮：换一条
        val refreshIntent = Intent(context, RandomReviewWidget::class.java).setAction(ACTION_REFRESH)
        views.setOnClickPendingIntent(
            R.id.widget_refresh,
            PendingIntent.getBroadcast(
                context, REQUEST_REFRESH, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        if (random == null) {
            views.setTextViewText(R.id.widget_content, "还没有记录\n去写下第一句吧")
            views.setViewVisibility(R.id.widget_image, View.GONE)
            views.setViewVisibility(R.id.widget_meta, View.GONE)
            return views
        }

        val (entry, tags) = random
        val text = entry.content.ifBlank { entry.imageDescription ?: "（无文字内容）" }
        views.setTextViewText(R.id.widget_content, text.take(MAX_CONTENT_CHARS))

        val thumbnail = entry.imagePath?.takeIf { it.isNotBlank() }?.let { ImageUtil.decodeThumbnail(it) }
        if (thumbnail != null) {
            views.setViewVisibility(R.id.widget_image, View.VISIBLE)
            views.setImageViewBitmap(R.id.widget_image, thumbnail)
        } else {
            views.setViewVisibility(R.id.widget_image, View.GONE)
        }

        val meta = buildString {
            append(DateFormats.formatShortDateTime(entry.createdAt))
            if (tags.isNotEmpty()) append(" · ").append(tags.joinToString(" · ") { it.label })
        }
        views.setTextViewText(R.id.widget_meta, meta)
        return views
    }

    companion object {
        const val ACTION_REFRESH = "com.gift.tolife.action.RANDOM_REVIEW_REFRESH"
        private const val REQUEST_OPEN = 3001
        private const val REQUEST_REFRESH = 3002
        private const val MAX_CONTENT_CHARS = 160
    }
}
