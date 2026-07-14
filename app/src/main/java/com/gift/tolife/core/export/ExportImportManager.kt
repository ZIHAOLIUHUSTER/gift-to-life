package com.gift.tolife.core.export

import android.content.Context
import android.net.Uri
import com.gift.tolife.core.database.dao.EntryDao
import com.gift.tolife.core.database.dao.EntryTagDao
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.EntryTag
import com.gift.tolife.core.model.EntryType
import com.gift.tolife.core.model.TagType
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportImportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entryDao: EntryDao,
    private val entryTagDao: EntryTagDao
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    // ---- Public API ----

    suspend fun exportToUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        val allEntries = entryDao.getAllEntriesAsList()
        val entryTagsMap = mutableMapOf<Long, MutableList<TagType>>()
        allEntries.forEach { e ->
            entryTagsMap[e.id] = entryTagDao.getByEntryId(e.id).map { it.tag }.toMutableList()
        }

        val manifestEntries = allEntries.map { entry ->
            val tags = entryTagsMap[entry.id]?.map { it.label } ?: emptyList()
            var imageEntry: String? = null
            var imageSha256: String? = null
            if (!entry.imagePath.isNullOrBlank()) {
                val file = File(entry.imagePath)
                if (file.exists()) {
                    imageEntry = "images/${file.name}"
                    imageSha256 = sha256(file)
                }
            }
            BackupV2Entry(
                id = entry.id,
                content = entry.content,
                type = entry.type.name,
                createdAt = entry.createdAt,
                updatedAt = entry.updatedAt,
                summaryStart = entry.summaryStart,
                summaryEnd = entry.summaryEnd,
                imageDescription = entry.imageDescription,
                isDeleted = entry.isDeleted,
                summaryModel = entry.summaryModel,
                tags = tags,
                imageEntry = imageEntry,
                imageSha256 = imageSha256
            )
        }

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            ZipOutputStream(BufferedOutputStream(outputStream)).use { zip ->
                // Write image files first
                allEntries.forEach { entry ->
                    if (!entry.imagePath.isNullOrBlank()) {
                        val file = File(entry.imagePath)
                        if (file.exists()) {
                            zip.putNextEntry(ZipEntry("images/${file.name}"))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
                // Write manifest last
                val manifest = BackupV2Manifest(
                    exportedAt = System.currentTimeMillis(),
                    entryCount = manifestEntries.size,
                    entries = manifestEntries
                )
                zip.putNextEntry(ZipEntry(BackupV2Config.MANIFEST_ENTRY_NAME))
                zip.write(gson.toJson(manifest).toByteArray())
                zip.closeEntry()
            }
        } ?: throw IOException("Cannot open output stream")

        allEntries.size
    }

    suspend fun replaceImportFromUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        val inputBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Cannot read input")

        val isZip = inputBytes.size >= 2 && inputBytes[0] == 'P'.code.toByte() && inputBytes[1] == 'K'.code.toByte()
        val isJson = inputBytes.isNotEmpty() && inputBytes[0] == '{'.code.toByte()

        when {
            isZip -> importV2(inputBytes)
            isJson -> importLegacyV1(String(inputBytes))
            else -> throw IOException("Unrecognized backup format")
        }
    }

    suspend fun importFromUri(uri: Uri): Int = replaceImportFromUri(uri)

    suspend fun clearAllEntries() {
        entryDao.deleteAll()
    }

    suspend fun getDeletedEntries(): List<Entry> = entryDao.getDeletedEntries()

    suspend fun restoreEntry(id: Long) {
        entryDao.restoreDeleted(id)
    }

    suspend fun permanentlyDeleteAllDeleted() {
        entryDao.permanentlyDeleteAllDeleted()
    }

    // ---- Private import helpers ----

    private suspend fun importV2(zipBytes: ByteArray): Int = withContext(Dispatchers.IO) {
        var manifest: BackupV2Manifest? = null
        val imageFiles = mutableMapOf<String, ByteArray>()

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val bytes = zip.readBytes()
                when {
                    entry.name == BackupV2Config.MANIFEST_ENTRY_NAME -> {
                        manifest = gson.fromJson(String(bytes), BackupV2Manifest::class.java)
                    }
                    entry.name.startsWith("images/") && entry.name.length > 7 -> {
                        imageFiles[entry.name] = bytes
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val m = manifest ?: throw IOException("Manifest not found in backup")
        if (m.formatVersion != 2) throw IOException("Unsupported backup version: ${m.formatVersion}")
        if (m.entries.size != m.entryCount) throw IOException("Entry count mismatch")
        if (m.entries.size > BackupV2Config.MAX_ENTRY_COUNT) throw IOException("Too many entries")

        // Save images to disk
        val imageMap = mutableMapOf<String, String>() // zipPath -> absolutePath
        val imagesDir = File(context.filesDir, "images")
        if (!imagesDir.exists()) imagesDir.mkdirs()
        imageFiles.forEach { (zipPath, bytes) ->
            val fileName = zipPath.removePrefix("images/")
            val destFile = File(imagesDir, fileName)
            destFile.writeBytes(bytes)
            imageMap[zipPath] = destFile.absolutePath
        }

        // Import entries in transaction
        entryDao.deleteAll()
        m.entries.forEach { e ->
            val imagePath = e.imageEntry?.let { imageMap[it] }
            val entry = Entry(
                id = 0,
                content = e.content,
                imagePath = imagePath,
                type = try {
                    EntryType.valueOf(e.type)
                } catch (_: Exception) {
                    EntryType.NORMAL
                },
                createdAt = e.createdAt,
                updatedAt = e.updatedAt,
                summaryStart = e.summaryStart,
                summaryEnd = e.summaryEnd,
                imageDescription = e.imageDescription,
                isDeleted = e.isDeleted,
                summaryModel = e.summaryModel
            )
            val entryId = entryDao.insert(entry)
            val tags = e.tags.mapNotNull { tag -> TagType.entries.find { it.label == tag } }
            if (tags.isNotEmpty()) {
                entryTagDao.insertAll(tags.map { EntryTag(entryId, it) })
            }
        }
        m.entries.size
    }

    private suspend fun importLegacyV1(json: String): Int = withContext(Dispatchers.IO) {
        val data = gson.fromJson(json, ExportData::class.java)
            ?: throw IOException("Invalid backup JSON")
        entryDao.deleteAll()
        var count = 0
        data.entries.forEach { e ->
            var imagePath: String? = null
            if (!e.imageBase64.isNullOrBlank()) {
                try {
                    val bytes = android.util.Base64.decode(e.imageBase64, android.util.Base64.NO_WRAP)
                    val imagesDir = File(context.filesDir, "images")
                    if (!imagesDir.exists()) imagesDir.mkdirs()
                    val file = File(imagesDir, "${UUID.randomUUID()}.webp")
                    file.writeBytes(bytes)
                    imagePath = file.absolutePath
                } catch (_: Exception) {
                    // Skip corrupted images
                }
            }
            val entry = Entry(
                content = e.content,
                type = try {
                    EntryType.valueOf(e.type)
                } catch (_: Exception) {
                    EntryType.NORMAL
                },
                createdAt = e.createdAt,
                imagePath = imagePath,
                imageDescription = e.imageDescription
            )
            val entryId = entryDao.insert(entry)
            val tags = e.tags.mapNotNull { tag -> TagType.entries.find { it.label == tag } }
            if (tags.isNotEmpty()) {
                entryTagDao.insertAll(tags.map { EntryTag(entryId, it) })
            }
            count++
        }
        count
    }

    // ---- Utilities ----

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytes = input.read(buffer)
            while (bytes > 0) {
                digest.update(buffer, 0, bytes)
                bytes = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
