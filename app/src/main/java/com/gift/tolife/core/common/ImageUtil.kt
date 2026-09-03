@file:Suppress("DEPRECATION")
package com.gift.tolife.core.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID

object ImageUtil {
    private const val MAX_DIMENSION = 1920
    private const val WEBP_QUALITY = 85

    suspend fun copyToPrivateDir(context: Context, sourceUri: Uri): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val imagesDir = File(context.filesDir, "images")
                if (!imagesDir.exists()) imagesDir.mkdirs()

                val fileName = "${UUID.randomUUID()}.webp"
                val destFile = File(imagesDir, fileName)

                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, options)
                }

                val sampleSize = calculateSampleSize(
                    options.outWidth, options.outHeight, MAX_DIMENSION
                )

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                val bitmap = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, decodeOptions)
                } ?: return@withContext null

                // Read EXIF orientation
                val rotation = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    val exif = ExifInterface(input)
                    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f

                val finalBitmap = if (rotation != 0f) {
                    val matrix = Matrix().apply { postRotate(rotation) }
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                } else {
                    bitmap
                }

                val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    Bitmap.CompressFormat.WEBP
                }

                try {
                    destFile.outputStream().use { output ->
                        finalBitmap.compress(format, WEBP_QUALITY, output)
                    }
                    destFile.absolutePath
                } finally {
                    if (finalBitmap !== bitmap) finalBitmap.recycle()
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        val maxSide = maxOf(width, height)
        while (maxSide / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        return sampleSize
    }

    /**
     * 解码本地图片为桌面小组件方形缩略图：中心裁剪为正方形 + 降采样 + RGB_565。
     * 先在代码里裁成正方形，任何宽高比图片渲染都不变形；解码失败返回 null。
     */
    fun decodeThumbnail(path: String, side: Int = 320): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val minSide = minOf(bounds.outWidth, bounds.outHeight)
            var sampleSize = 1
            while (minSide / sampleSize > side) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeFile(path, decodeOptions) ?: return null
            val crop = minOf(bitmap.width, bitmap.height)
            val x = (bitmap.width - crop) / 2
            val y = (bitmap.height - crop) / 2
            if (crop == bitmap.width && crop == bitmap.height) bitmap
            else Bitmap.createBitmap(bitmap, x, y, crop, crop)
        } catch (e: Exception) {
            null
        }
    }
}
