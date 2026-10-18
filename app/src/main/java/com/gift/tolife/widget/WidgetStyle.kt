package com.gift.tolife.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.gift.tolife.R
import com.gift.tolife.core.ui.theme.ThemeVariant
import com.gift.tolife.core.ui.theme.remapLightness
import com.gift.tolife.core.ui.theme.toPalette
import kotlin.math.max

/** 小组件用的一组实色；颜色来自当前皮肤，不再写死暖白。 */
internal data class WidgetPalette(
    val card: Int,
    val border: Int,
    val ink: Int,
    val muted: Int,
    val accent: Int,
)

/**
 * 桌面小组件的视觉适配。
 *
 * RemoteViews 不能用 Compose，但可以：
 * - 用两层白色 shape（填充 + 发丝边）做底，再用 ImageView.setColorFilter 染成当前皮肤；
 * - 用 setTextColor 把正文 / 元信息 / 点睛色换成当前皮肤；
 * - 用圆角化的 Bitmap 让照片也带一点“相纸”的边。
 *
 * 这样 light/dark、六套皮肤都能跟随，而不用为每套皮肤复制 12 份 XML。
 */
internal object WidgetStyle {
    fun read(context: Context): WidgetPalette {
        val prefs = context.getSharedPreferences("gift_settings", Context.MODE_PRIVATE)
        val variant = ThemeVariant.fromName(prefs.getString("theme_variant", ThemeVariant.DEFAULT.name))
        val mode = prefs.getString("theme_mode", "system") ?: "system"
        val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val dark = when (mode) {
            "dark" -> true
            "light" -> false
            else -> systemDark
        }
        val palette = variant.toPalette(dark)
        return WidgetPalette(
            card = palette.surfaceContainer.toArgb(),
            border = palette.outlineVariant.toArgb(),
            ink = palette.onSurface.toArgb(),
            muted = palette.onSurfaceVariant.toArgb(),
            // 浅色下小字用压暗的主色（对纸底 ≥4.5:1）；深色下直接用派生后的亮主色。
            accent = if (dark) palette.primary.toArgb() else palette.primary.remapLightness(0.32f, 1f).toArgb(),
        )
    }

    fun applyCard(views: RemoteViews, palette: WidgetPalette) {
        views.setInt(R.id.widget_card_fill, "setColorFilter", palette.card)
        views.setInt(R.id.widget_card_border, "setColorFilter", palette.border)
    }

    fun applyRandom(views: RemoteViews, palette: WidgetPalette) {
        applyCard(views, palette)
        views.setInt(R.id.widget_rule, "setColorFilter", palette.accent)
        views.setTextColor(R.id.widget_label, palette.accent)
        views.setTextColor(R.id.widget_content, palette.ink)
        views.setTextColor(R.id.widget_meta, palette.muted)
        views.setTextColor(R.id.widget_refresh_label, palette.accent)
        views.setInt(R.id.widget_refresh_icon, "setColorFilter", palette.accent)
    }

    fun applyQuick(views: RemoteViews, palette: WidgetPalette) {
        applyCard(views, palette)
        views.setTextColor(R.id.widget_quick_label, palette.accent)
    }

    /** 中心裁剪成方图 + 圆角 + 1dp 相纸边；透明角外让卡片纸面透出来。 */
    fun roundedPhoto(source: Bitmap, borderColor: Int): Bitmap {
        val side = minOf(source.width, source.height).coerceAtLeast(1)
        val output = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val rect = RectF(0f, 0f, side.toFloat(), side.toFloat())
        val radius = side * 0.12f
        val path = Path().apply {
            addRoundRect(rect, radius, radius, Path.Direction.CW)
        }

        canvas.save()
        canvas.clipPath(path)
        val left = (source.width - side) / 2
        val top = (source.height - side) / 2
        val src = Rect(left, top, left + side, top + side)
        canvas.drawBitmap(
            source,
            src,
            rect,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        canvas.restore()

        val strokeWidth = max(1f, side / 160f)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            color = borderColor
        }
        val inset = strokeWidth / 2f
        canvas.drawRoundRect(
            RectF(inset, inset, side - inset, side - inset),
            radius,
            radius,
            border,
        )
        return output
    }
}

/** 设置里换肤/换深浅后，让已经放在桌面上的小组件立刻跟上。 */
object GiftWidgets {
    fun requestRestyle(context: Context) {
        context.sendBroadcast(
            Intent(context, RandomReviewWidget::class.java)
                .setAction(RandomReviewWidget.ACTION_RESTYLE)
        )
        context.sendBroadcast(
            Intent(context, QuickEntryWidget::class.java)
                .setAction(QuickEntryWidget.ACTION_RESTYLE)
        )
    }
}
