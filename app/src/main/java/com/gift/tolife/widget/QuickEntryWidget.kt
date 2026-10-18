package com.gift.tolife.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.gift.tolife.R

/**
 * 快速记录小组件：一张小纸签，点一下直接进记录页并聚焦输入框。
 * 2026-10-18：跟随皮肤/深色，和随机回顾共用纸面 + 发丝边。
 */
class QuickEntryWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildViews(context, id))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_RESTYLE) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, QuickEntryWidget::class.java))
            for (id in ids) {
                manager.updateAppWidget(id, buildViews(context, id))
            }
        } else {
            super.onReceive(context, intent)
        }
    }

    private fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.quick_entry_widget)
        WidgetStyle.applyQuick(views, WidgetStyle.read(context))

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("request_composer_focus", true)
        }
        if (intent != null) {
            views.setOnClickPendingIntent(
                R.id.widget_container,
                PendingIntent.getActivity(
                    context, appWidgetId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
        return views
    }

    companion object {
        const val ACTION_RESTYLE = "com.gift.tolife.action.QUICK_ENTRY_RESTYLE"
    }
}
