package com.gift.tolife.core.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gift.tolife.MainActivity
import com.gift.tolife.R
import com.gift.tolife.core.common.SummaryDeepLink
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「上周的你，整理好了」通知。
 *
 * 没有通知权限时不报错、静默跳过——生成本身仍然完成，用户进 App 就能看到。
 */
@Singleton
class SummaryNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        const val CHANNEL_ID = "summary_ready"
        const val CHANNEL_NAME = "回顾写好了"
        const val CHANNEL_DESC = "周志/月志生成完成时提醒你"

        /** 在 Application.onCreate 里调用一次即可（重复调用无副作用）。 */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = CHANNEL_DESC
                }
            )
        }
    }

    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun notifyReady(entryId: Long, keyword: String, isWeek: Boolean) {
        if (!canNotify()) return
        val title = if (isWeek) "上周的你，整理好了" else "上个月的你，整理好了"
        val text = keyword.ifBlank { if (isWeek) "点开看看这一周" else "点开看看这个月" }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(SummaryDeepLink.EXTRA_ENTRY_ID, entryId)
        }
        val pending = PendingIntent.getActivity(
            context,
            entryId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(entryId.toInt(), notification)
        }
    }
}
