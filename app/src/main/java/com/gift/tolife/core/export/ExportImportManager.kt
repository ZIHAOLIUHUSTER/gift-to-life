package com.gift.tolife.core.export

import android.content.Context
import android.net.Uri
import com.gift.tolife.core.database.AppDatabase
import com.gift.tolife.core.database.StagedImportEntry
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
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportImportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entryDao: EntryDao,
    private val entryTagDao: EntryTagDao,
    private val database: AppDatabase
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

    // ---- Safe Import ----

    /**
     * Copy URI content to a temporary file, streaming with a 2 GiB cap.
     */
    private fun copyToTempFile(uri: Uri): File {
        val target = File.createTempFile("gift-import-", ".bin", context.cacheDir)
        var total = 0L
        context.contentResolver.openInputStream(uri)!!.use { input ->
            target.outputStream().buffered().use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    check(total <= BackupV2Config.MAX_BACKUP_FILE_BYTES) { "备份文件超过 2 GiB" }
                    output.write(buffer, 0, read)
                }
            }
        }
        return target
    }

    /**
     * Reject path traversal and other malicious image entry names.
     */
    private fun requireSafeImageEntry(name: String) {
        require(name.startsWith("images/")) { "非法路径: $name" }
        require(!name.contains("\\")) { "包含反斜杠: $name" }
        require(!name.contains("..")) { "包含路径穿越: $name" }
        require(name.substringAfter("images/").isNotEmpty()) { "空文件名" }
    }

    suspend fun replaceImportFromUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        val tempFile = copyToTempFile(uri)
        try {
            val firstBytes = tempFile.inputStream().use { stream ->
                val buf = ByteArray(2)
                if (stream.read(buf) != 2) throw IOException("文件太短")
                buf
            }
            when {
                firstBytes.contentEquals(ZIP_MAGIC) -> importV2Safe(tempFile)
                firstBytes[0] == '{'.code.toByte() -> importLegacyV1Safe(tempFile)
                else -> throw IOException("不支持的备份格式")
            }
        } finally {
            tempFile.delete()
        }
    }

    suspend fun importFromUri(uri: Uri): Int = replaceImportFromUri(uri)

    // ---- V2 Safe Import ----

    private suspend fun importV2Safe(tempFile: File): Int {
        val stagedEntries = mutableListOf<StagedImportEntry>()
        val imageMap = mutableMapOf<String, File>() // source entry name -> temp image file

        java.util.zip.ZipFile(tempFile).use { zip ->
            // Phase 1: Parse manifest
            val manifestEntry = zip.getEntry(BackupV2Config.MANIFEST_ENTRY_NAME)
                ?: throw IOException("备份缺少 manifest.json")
            val manifest = zip.getInputStream(manifestEntry).use { stream ->
                gson.fromJson(String(stream.readBytes()), BackupV2Manifest::class.java)
            }
            require(manifest.formatVersion == 2) { "不支持的备份版本: ${manifest.formatVersion}" }
            require(manifest.entryCount == manifest.entries.size) { "条目数不匹配" }
            require(manifest.entries.size <= BackupV2Config.MAX_ENTRY_COUNT) { "条目数超过上限" }

            // Phase 2: Extract images to temp directory
            val imageTempDir = File(context.cacheDir, "import-images-${UUID.randomUUID()}")
            imageTempDir.mkdirs()
            val imageEntries = zip.entries().asSequence()
                .filter { it.name != BackupV2Config.MANIFEST_ENTRY_NAME }
                .toList()

            var totalImageBytes = 0L
            imageEntries.forEach { entry ->
                requireSafeImageEntry(entry.name)
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                require(bytes.size.toLong() <= BackupV2Config.MAX_SINGLE_IMAGE_BYTES) {
                    "图片过大: ${entry.name}"
                }
                totalImageBytes += bytes.size
                require(totalImageBytes <= BackupV2Config.MAX_TOTAL_IMAGE_BYTES) {
                    "图片总大小超过限制"
                }
                val tempImageFile = File(imageTempDir, entry.name.substringAfter("images/"))
                tempImageFile.writeBytes(bytes)
                imageMap[entry.name] = tempImageFile
            }

            // Phase 3: Build staged entries
            val finalImagesDir = File(context.filesDir, "images").apply { mkdirs() }
            manifest.entries.forEach { e ->
                var finalPath: String? = null
                e.imageEntry?.let { imageEntry ->
                    val tempImage = imageMap[imageEntry]
                        ?: throw IOException("声明了图片但 ZIP 中不存在: $imageEntry")
                    val fileName = "${UUID.randomUUID()}.webp"
                    val dest = File(finalImagesDir, fileName)
                    tempImage.copyTo(dest, overwrite = true)
                    finalPath = dest.absolutePath
                }
                stagedEntries.add(StagedImportEntry(
                    entry = Entry(
                        content = e.content,
                        type = try { EntryType.valueOf(e.type) } catch (_: Exception) { EntryType.NORMAL },
                        createdAt = e.createdAt,
                        updatedAt = e.updatedAt,
                        imagePath = finalPath,
                        imageDescription = e.imageDescription,
                        isDeleted = e.isDeleted,
                        summaryStart = e.summaryStart,
                        summaryEnd = e.summaryEnd,
                        summaryModel = e.summaryModel
                    ),
                    tags = e.tags.mapNotNull { tag -> TagType.entries.find { it.label == tag } }
                ))
            }

            // Phase 4: Capture old image paths for cleanup *before* the transaction
            val oldPaths = entryDao.getAllEntriesAsList().mapNotNull { it.imagePath }.toSet()

            // Phase 5: Replace in single Room transaction
            database.replaceAll(stagedEntries)

            // Phase 6: Clean old images (only after successful commit)
            oldPaths.forEach { File(it).delete() }

            // Clean temp
            imageTempDir.deleteRecursively()
        }

        return stagedEntries.size
    }

    // ---- V1 Safe Import ----

    private suspend fun importLegacyV1Safe(tempFile: File): Int {
        val json = String(tempFile.readBytes())
        val data = gson.fromJson(json, ExportData::class.java)
            ?: throw IOException("JSON 解析失败")
        require(data.entries.size <= BackupV2Config.MAX_ENTRY_COUNT) { "条目数超过上限" }

        val stagedEntries = mutableListOf<StagedImportEntry>()
        val imagesDir = File(context.filesDir, "images").apply { mkdirs() }

        data.entries.forEach { e ->
            var imagePath: String? = null
            if (!e.imageBase64.isNullOrBlank()) {
                val bytes = android.util.Base64.decode(e.imageBase64, android.util.Base64.NO_WRAP)
                require(bytes.size.toLong() <= BackupV2Config.MAX_SINGLE_IMAGE_BYTES) { "图片过大" }
                val file = File(imagesDir, "${UUID.randomUUID()}.webp")
                file.writeBytes(bytes)
                imagePath = file.absolutePath
            }
            stagedEntries.add(StagedImportEntry(
                entry = Entry(
                    content = e.content,
                    type = try { EntryType.valueOf(e.type) } catch (_: Exception) { EntryType.NORMAL },
                    createdAt = e.createdAt,
                    imagePath = imagePath,
                    imageDescription = e.imageDescription
                ),
                tags = e.tags.mapNotNull { tag -> TagType.entries.find { it.label == tag } }
            ))
        }

        val oldPaths = entryDao.getAllEntriesAsList().mapNotNull { it.imagePath }.toSet()
        database.replaceAll(stagedEntries)
        oldPaths.forEach { File(it).delete() }

        return stagedEntries.size
    }

    // ---- Data management ----

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

    companion object {
        private val ZIP_MAGIC = byteArrayOf('P'.code.toByte(), 'K'.code.toByte())
    }
}
