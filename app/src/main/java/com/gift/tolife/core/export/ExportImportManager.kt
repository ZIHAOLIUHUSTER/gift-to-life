package com.gift.tolife.core.export

import android.content.Context
import android.net.Uri
import com.gift.tolife.core.database.EntryRepository
import com.gift.tolife.core.database.dao.EntryDao
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.EntryType
import com.gift.tolife.core.model.TagType
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportImportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: EntryRepository,
    private val entryDao: EntryDao
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun exportToUri(uri: Uri): Int {
        return withContext(Dispatchers.IO) {
            val entries = repository.getAllEntries().first()
            val exportEntries = entries.map { entry ->
                val tags = repository.getTags(entry.id).map { it.tag.label }
                ExportEntry(
                    content = entry.content,
                    type = entry.type.name,
                    createdAt = entry.createdAt,
                    tags = tags,
                    imageBase64 = entry.imagePath?.let { path ->
                        try { android.util.Base64.encodeToString(java.io.File(path).readBytes(), android.util.Base64.NO_WRAP) } catch (_: Exception) { null }
                    },
                    imageDescription = entry.imageDescription
                )
            }
            val data = ExportData(entries = exportEntries)
            val json = gson.toJson(data)
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(json.toByteArray())
            }
            exportEntries.size
        }
    }

    suspend fun importFromUri(uri: Uri): Int {
        return withContext(Dispatchers.IO) {
            val json = context.contentResolver.openInputStream(uri)?.use {
                String(it.readBytes())
            } ?: return@withContext 0
            val data = gson.fromJson(json, ExportData::class.java) ?: return@withContext 0
            var count = 0
            data.entries.forEach { e ->
                var imagePath: String? = null
                if (!e.imageBase64.isNullOrBlank()) {
                    try {
                        val bytes = android.util.Base64.decode(e.imageBase64, android.util.Base64.NO_WRAP)
                        val imagesDir = java.io.File(context.filesDir, "images")
                        if (!imagesDir.exists()) imagesDir.mkdirs()
                        val fileName = "${java.util.UUID.randomUUID()}.webp"
                        val destFile = java.io.File(imagesDir, fileName)
                        destFile.writeBytes(bytes)
                        imagePath = destFile.absolutePath
                    } catch (_: Exception) {}
                }
                val entry = Entry(
                    content = e.content,
                    type = try { EntryType.valueOf(e.type) } catch (_: Exception) { EntryType.NORMAL },
                    createdAt = e.createdAt,
                    imagePath = imagePath,
                    imageDescription = e.imageDescription
                )
                val entryId = entryDao.insert(entry)
                val tags = e.tags.mapNotNull { tag ->
                    TagType.entries.find { it.label == tag }
                }
                if (tags.isNotEmpty()) {
                    repository.setTags(entryId, tags)
                }
                count++
            }
            count
        }
    }

    suspend fun clearAllEntries() {
        entryDao.deleteAll()
    }

    suspend fun replaceImportFromUri(uri: Uri): Int {
        return withContext(Dispatchers.IO) {
            // 清空现有数据
            entryDao.deleteAll()
            // 重新导入
            val json = context.contentResolver.openInputStream(uri)?.use {
                String(it.readBytes())
            } ?: return@withContext 0
            val data = gson.fromJson(json, ExportData::class.java) ?: return@withContext 0
            var count = 0
            data.entries.forEach { e ->
                var imagePath: String? = null
                if (!e.imageBase64.isNullOrBlank()) {
                    try {
                        val bytes = android.util.Base64.decode(e.imageBase64, android.util.Base64.NO_WRAP)
                        val imagesDir = java.io.File(context.filesDir, "images")
                        if (!imagesDir.exists()) imagesDir.mkdirs()
                        val fileName = "${java.util.UUID.randomUUID()}.webp"
                        val destFile = java.io.File(imagesDir, fileName)
                        destFile.writeBytes(bytes)
                        imagePath = destFile.absolutePath
                    } catch (_: Exception) {}
                }
                val entry = Entry(
                    content = e.content,
                    type = try { EntryType.valueOf(e.type) } catch (_: Exception) { EntryType.NORMAL },
                    createdAt = e.createdAt,
                    imagePath = imagePath,
                    imageDescription = e.imageDescription
                )
                val entryId = entryDao.insert(entry)
                val tags = e.tags.mapNotNull { tag ->
                    TagType.entries.find { it.label == tag }
                }
                if (tags.isNotEmpty()) {
                    repository.setTags(entryId, tags)
                }
                count++
            }
            count
        }
    }
}
