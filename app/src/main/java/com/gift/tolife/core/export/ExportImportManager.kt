package com.gift.tolife.core.export

import android.content.Context
import android.net.Uri
import com.gift.tolife.core.database.AppDatabase
import com.gift.tolife.core.database.StagedImportEntry
import com.gift.tolife.core.database.dao.EntryDao
import com.gift.tolife.core.database.dao.EntryTagDao
import com.gift.tolife.core.datastore.SettingsDataStore
import com.gift.tolife.core.model.Entry
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
    private val database: AppDatabase,
    private val settingsDataStore: SettingsDataStore
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    // ---- Public API ----

    suspend fun exportToUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        val pageSize = 100
        var lastId = 0L
        var totalCount = 0
        val manifestEntries = mutableListOf<BackupV2Entry>()

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            ZipOutputStream(BufferedOutputStream(outputStream)).use { zip ->
                while (true) {
                    val batch = entryDao.getEntriesAfterId(lastId, pageSize)
                    if (batch.isEmpty()) break
                    totalCount += batch.size
                    lastId = batch.last().id

                    // 批量获取标签
                    val batchIds = batch.map { it.id }
                    val tagMap = mutableMapOf<Long, MutableList<String>>()
                    batchIds.chunked(500).forEach { chunk ->
                        entryTagDao.getByEntryIds(chunk).forEach { tag ->
                            tagMap.getOrPut(tag.entryId) { mutableListOf() }.add(tag.tag.label)
                        }
                    }

                    batch.forEach { entry ->
                        val tags = tagMap[entry.id] ?: emptyList()
                        var imageEntry: String? = null
                        var imageSha256: String? = null
                        if (!entry.imagePath.isNullOrBlank()) {
                            val file = File(entry.imagePath)
                            if (file.exists()) {
                                imageEntry = "images/${file.name}"
                                imageSha256 = sha256(file)
                                zip.putNextEntry(ZipEntry(imageEntry))
                                file.inputStream().use { it.copyTo(zip) }
                                zip.closeEntry()
                            }
                        }
                        manifestEntries.add(BackupV2Entry(
                            id = entry.id, content = entry.content, type = entry.type.name,
                            createdAt = entry.createdAt, updatedAt = entry.updatedAt,
                            summaryStart = entry.summaryStart, summaryEnd = entry.summaryEnd,
                            imageDescription = entry.imageDescription, isDeleted = entry.isDeleted,
                            summaryModel = entry.summaryModel, tags = tags,
                            imageEntry = imageEntry, imageSha256 = imageSha256
                        ))
                    }
                }

                // 最后写 manifest
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

        totalCount
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
            val result = when {
                firstBytes.contentEquals(ZIP_MAGIC) -> importV2Safe(tempFile)
                firstBytes[0] == '{'.code.toByte() -> importLegacyV1Safe(tempFile)
                else -> throw IOException("不支持的备份格式")
            }
            // 导入成功后清空 streak 缓存
            settingsDataStore.updateStreak(0, 0)
            result
        } finally {
            tempFile.delete()
        }
    }

    suspend fun importFromUri(uri: Uri): Int = replaceImportFromUri(uri)

    // ---- V2 Safe Import (staging strategy) ----

    private suspend fun importV2Safe(tempFile: File): Int {
        val stagedImages = mutableMapOf<String, StagedImage>() // sourceEntryName -> StagedImage
        val finalImagePaths = mutableMapOf<String, String>()    // sourceEntryName -> final absolute path

        val stagingDir = File(context.cacheDir, "import-staging-${UUID.randomUUID()}").apply { mkdirs() }
        try {
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

                // Phase 2: Validate and stage images to cacheDir/import-staging
                var totalImageBytes = 0L

                manifest.entries.forEach { e ->
                    e.imageEntry?.let { imageEntry ->
                        requireSafeImageEntry(imageEntry)
                        val zipEntry = zip.getEntry(imageEntry)
                            ?: throw IOException("清单声明了图片但 ZIP 中不存在: $imageEntry")
                        val size = zipEntry.size
                        require(size <= BackupV2Config.MAX_SINGLE_IMAGE_BYTES) { "图片过大: $imageEntry" }
                        totalImageBytes += size
                        require(totalImageBytes <= BackupV2Config.MAX_TOTAL_IMAGE_BYTES) { "图片总大小超过限制" }

                        // Stream hash computation while writing to staging
                        val digest = java.security.MessageDigest.getInstance("SHA-256")
                        val stageFile = File(stagingDir, imageEntry.substringAfter("images/"))
                        var actualDecompressed = 0L
                        zip.getInputStream(zipEntry).use { input ->
                            stageFile.outputStream().use { output ->
                                val buffer = ByteArray(8192)
                                var bytes = input.read(buffer)
                                while (bytes > 0) {
                                    actualDecompressed += bytes
                                    require(actualDecompressed <= BackupV2Config.MAX_SINGLE_IMAGE_BYTES) {
                                        "图片实际大小超过限制: $imageEntry"
                                    }
                                    digest.update(buffer, 0, bytes)
                                    output.write(buffer, 0, bytes)
                                    bytes = input.read(buffer)
                                }
                            }
                        }
                        val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                        require(e.imageSha256 != null) { "图片缺少 SHA-256: $imageEntry" }
                        require(actualHash == e.imageSha256) { "图片哈希不匹配: $imageEntry" }

                        stagedImages[imageEntry] = StagedImage(stageFile, actualHash)
                    }
                }

                // Phase 3: Move staged images to final location (filesDir/images)
                val imagesDir = File(context.filesDir, "images").apply { mkdirs() }
                try {
                    stagedImages.forEach { (sourceName, staged) ->
                        val finalName = "${UUID.randomUUID()}.webp"
                        val finalFile = File(imagesDir, finalName)
                        staged.file.copyTo(finalFile, overwrite = true)
                        finalImagePaths[sourceName] = finalFile.absolutePath
                    }
                } catch (e: Exception) {
                    // 清理已复制到正式目录的图片
                    finalImagePaths.values.forEach { File(it).delete() }
                    throw e
                }

                // Phase 4: Build staged entries with final paths
                val stagedEntries = manifest.entries.map { e ->
                    val finalPath = e.imageEntry?.let { finalImagePaths[it] }
                    StagedImportEntry(
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
                    )
                }

                // Phase 5: Capture old paths, then DB transaction
                val oldPaths = entryDao.getAllImagePaths().toSet()
                try {
                    database.replaceAll(stagedEntries)
                } catch (e: Exception) {
                    // Rollback: delete newly created final images
                    finalImagePaths.values.forEach { File(it).delete() }
                    throw e
                }

                // Phase 6: Delete old images (only after successful commit)
                oldPaths.forEach { File(it).delete() }

                return stagedEntries.size
            }
        } catch (e: Exception) {
            stagingDir.deleteRecursively()
            throw e
        } finally {
            if (stagingDir.exists()) stagingDir.deleteRecursively()
        }
    }

    // ---- V1 Safe Import (staging strategy) ----

    private suspend fun importLegacyV1Safe(tempFile: File): Int = withContext(Dispatchers.IO) {
        val json = tempFile.inputStream().buffered().use { String(it.readBytes()) }
        val data = gson.fromJson(json, ExportData::class.java)
            ?: throw IOException("JSON 解析失败")
        require(data.entries.size <= BackupV2Config.MAX_ENTRY_COUNT) { "条目数超过上限" }

        val stagedEntries = mutableListOf<StagedImportEntry>()
        val imagesDir = File(context.filesDir, "images").apply { mkdirs() }
        var totalImageBytes = 0L

        data.entries.forEach { e ->
            var imagePath: String? = null
            if (!e.imageBase64.isNullOrBlank()) {
                val bytes = android.util.Base64.decode(e.imageBase64, android.util.Base64.NO_WRAP)
                require(bytes.size.toLong() <= BackupV2Config.MAX_SINGLE_IMAGE_BYTES) { "图片过大" }
                totalImageBytes += bytes.size
                require(totalImageBytes <= BackupV2Config.MAX_TOTAL_IMAGE_BYTES) { "图片总大小超过限制" }
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

        val oldPaths = entryDao.getAllImagePaths().toSet()
        try {
            database.replaceAll(stagedEntries)
        } catch (e: Exception) {
            // 回滚：删除本次导入的图片
            stagedEntries.forEach { it.entry.imagePath?.let { path -> File(path).delete() } }
            throw e
        }
        oldPaths.forEach { File(it).delete() }

        stagedEntries.size
    }

    // ---- Data management ----

    suspend fun clearAllEntries() {
        val oldPaths = entryDao.getAllImagePaths().toSet()
        entryDao.deleteAll()
        oldPaths.forEach { File(it).delete() }
    }

    suspend fun getDeletedEntries(): List<Entry> = entryDao.getDeletedEntries()

    suspend fun restoreEntry(id: Long) {
        entryDao.restoreDeleted(id)
    }

    suspend fun permanentlyDeleteAllDeleted() = withContext(Dispatchers.IO) {
        val deleted = entryDao.getDeletedEntries()
        val imagePaths = deleted.mapNotNull { it.imagePath }
        entryDao.permanentlyDeleteAllDeleted()
        imagePaths.forEach { runCatching { File(it).delete() } }
    }

    // ---- Utilities ----

    private fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
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

    private data class StagedImage(val file: File, val hash: String)

    companion object {
        private val ZIP_MAGIC = byteArrayOf('P'.code.toByte(), 'K'.code.toByte())
    }
}