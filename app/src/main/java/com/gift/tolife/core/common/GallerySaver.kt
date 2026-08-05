package com.gift.tolife.core.common

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GallerySaver {
    private const val RELATIVE_PATH = "Pictures/GiftToLife"

    fun saveImage(context: Context, imagePath: String, entryTimestamp: Long): Boolean {
        val file = File(imagePath)
        if (!file.exists()) return false

        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(entryTimestamp))
        val fileName = "gift_$dateStr.webp"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToMediaStore(context, file, fileName)
            } else {
                saveToLegacyStorage(context, file, fileName)
            }
        } catch (e: Exception) {
            false
        }
    }

    @android.annotation.SuppressLint("InlinedApi")
    private fun saveToMediaStore(context: Context, file: File, fileName: String): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/webp")
            put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            return true
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            return false
        }
    }

    private fun saveToLegacyStorage(context: Context, file: File, fileName: String): Boolean {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GiftToLife")
        if (!dir.exists()) dir.mkdirs()
        val dest = File(dir, fileName)
        file.inputStream().use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
        // Notify media scanner
        MediaStore.Images.Media.insertImage(
            context.contentResolver, dest.absolutePath, fileName, "Saved from Gift To Life"
        )
        return true
    }
}