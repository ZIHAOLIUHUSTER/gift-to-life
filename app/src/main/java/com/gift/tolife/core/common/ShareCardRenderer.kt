package com.gift.tolife.core.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.gift.tolife.core.common.DateFormats

object ShareCardRenderer {
    private const val WIDTH = 1080
    private const val PAD = 80
    private const val CONTENT_W = WIDTH - 2 * PAD
    private const val MAX_IMAGE_H = 480
    private const val MAX_TEXT_LINES = 12

    // 暖白 + 墨绿 + 深灰（与 App 主题一致）
    private const val BG = 0xFFFAF8F5.toInt()
    private const val CARD = 0xFFFFFCF8.toInt()
    private const val BORDER = 0xFFE8E3D8.toInt()
    private const val TEXT = 0xFF3D392E.toInt()
    private const val SECONDARY = 0xFF8B8577.toInt()
    private const val ACCENT = 0xFF5B7B6F.toInt()
    private const val WATERMARK = 0xFFB8B2A4.toInt()

    fun render(content: String, timestamp: Long, imagePath: String?): Bitmap? {
        return try {
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TEXT
                textSize = 52f
                typeface = Typeface.DEFAULT
            }
            val displayContent = content.trim().ifEmpty { "（无内容）" }
            val layout = StaticLayout.Builder
                .obtain(displayContent, 0, displayContent.length, textPaint, CONTENT_W)
                .setLineSpacing(24f, 1f)
                .setMaxLines(MAX_TEXT_LINES)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()

            var imageBitmap: Bitmap? = null
            if (!imagePath.isNullOrBlank()) {
                imageBitmap = decodeSampled(imagePath, CONTENT_W, MAX_IMAGE_H)
            }
            val imageH = imageBitmap?.let {
                (it.height * CONTENT_W / it.width).coerceAtMost(MAX_IMAGE_H)
            } ?: 0

            val quoteH = 110
            val footerH = 130
            val height = PAD + (if (imageH > 0) imageH + 40 else 0) + quoteH + layout.height + footerH + PAD

            val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(BG)

            // 圆角卡片
            val cardRect = RectF(20f, 20f, WIDTH - 20f, height - 20f)
            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD }
            canvas.drawRoundRect(cardRect, 48f, 48f, cardPaint)
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = BORDER
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawRoundRect(cardRect, 48f, 48f, borderPaint)

            var y = PAD
            // 顶部图片
            if (imageBitmap != null && imageH > 0) {
                val x = (WIDTH - CONTENT_W) / 2
                canvas.drawBitmap(imageBitmap, null, Rect(x, y, x + CONTENT_W, y + imageH), Paint(Paint.ANTI_ALIAS_FLAG))
                y += imageH + 40
            }

            // 中文引号装饰
            val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ACCENT
                textSize = 110f
                typeface = Typeface.DEFAULT
            }
            canvas.drawText("「", PAD.toFloat(), y + 100f, quotePaint)
            y += quoteH

            // 正文
            canvas.save()
            canvas.translate((PAD + 30).toFloat(), y.toFloat())
            layout.draw(canvas)
            canvas.restore()
            y += layout.height

            // 底部：时间 + 水印
            val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = SECONDARY
                textSize = 34f
            }
            canvas.drawText(DateFormats.formatDateTime(timestamp), PAD.toFloat(), height - 90f, timePaint)
            val wmPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = WATERMARK
                textSize = 30f
            }
            val wm = "Gift To Life"
            val wmW = wmPaint.measureText(wm)
            canvas.drawText(wm, WIDTH - PAD - wmW, height - 90f, wmPaint)

            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeSampled(path: String, reqW: Int, reqH: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= reqW || bounds.outHeight / (sample * 2) >= reqH) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(path, opts)
        } catch (e: Exception) {
            null
        }
    }
}