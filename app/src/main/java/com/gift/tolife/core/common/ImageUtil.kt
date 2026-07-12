package com.gift.tolife.core.common

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

object ImageUtil {
    suspend fun copyToPrivateDir(context: Context, sourceUri: Uri): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val imagesDir = File(context.filesDir, "images")
                if (!imagesDir.exists()) imagesDir.mkdirs()

                val extension = getExtension(context, sourceUri)
                val fileName = "${UUID.randomUUID()}.$extension"
                val destFile = File(imagesDir, fileName)

                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                destFile.absolutePath
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun getExtension(context: Context, uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri)
        return when {
            mimeType?.contains("png") == true -> "png"
            mimeType?.contains("webp") == true -> "webp"
            mimeType?.contains("gif") == true -> "gif"
            else -> "jpg"
        }
    }
}
