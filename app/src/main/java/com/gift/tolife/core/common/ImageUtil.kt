@file:Suppress("DEPRECATION")
package com.gift.tolife.core.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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

                try {
                    destFile.outputStream().use { output ->
                        bitmap.compress(Bitmap.CompressFormat.WEBP, WEBP_QUALITY, output)
                    }
                    destFile.absolutePath
                } finally {
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
}
