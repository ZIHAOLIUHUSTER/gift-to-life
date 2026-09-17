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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
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

    /**
     * 导出到目标 URI。
     *
     * 安全设计：先写 cacheDir 临时文件 → 结构校验 → 再复制到目标 URI。
     * 任何失败都发生在打开目标文件之前，不会把用户上一份备份截断成半截文件；
     * 整个流程 NonCancellable，用户中途离开页面也能写完。
     */
    suspend fun exportToUri(uri: Uri): ExportResult = withContext(Dispatchers.IO + NonCancellable) {
        val tempFile = File.createTempFile(TEMP_EXPORT_PREFIX, TEMP_EXPORT_SUFFIX, context.cacheDir)
        try {
            val result = writeArchive(tempFile)
            BackupArchiveVerifier.verify(tempFile)
            copyToUri(tempFile, uri)
            result
        } finally {
            tempFile.delete()
        }
    }

    private suspend fun writeArchive(target: File): ExportResult {
        val pageSize = 100
        var lastId = 0L
        return target.outputStream().use { outputStream ->
            V2ArchiveWriter(outputStream).use { writer ->
                while (true) {
                    // 导出含回收站：这个查询不带 isDeleted = 0 过滤
                    val batch = entryDao.getEntriesForExportAfterId(lastId, pageSize)
                    if (batch.isEmpty()) break
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
                        val imageFile = entry.imagePath?.takeIf { it.isNotBlank() }?.let(::File)
                        writer.addEntry(entry, tagMap[entry.id] ?: emptyList(), imageFile)
                    }
                }
                writer.finish()
            }
        }
    }

    private fun copyToUri(source: File, uri: Uri) {
        val output = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("无法写入目标文件")
        output.use { out ->
            source.inputStream().use { it.copyTo(out) }
        }
    }

    // ---- Safe Import ----

    /**
     * Copy URI content to a temporary file, streaming with a 2 GiB cap.
     */
    private fun copyToTempFile(uri: Uri): File {
        val target = File.createTempFile("gift-import-", ".bin", context.cacheDir)
        try {
            var total = 0L
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("无法打开文件")
            input.use { stream ->
                target.outputStream().buffered().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        total += read
                        check(total <= BackupV2Config.MAX_BACKUP_FILE_BYTES) { "备份文件超过 2 GiB" }
                        output.write(buffer, 0, read)
                    }
                }
            }
            return target
        } catch (t: Throwable) {
            // 失败时不留半截临时文件在 cacheDir
            target.delete()
            throw t
        }
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

    private data class StagedImage(val file: File, val hash: String)

    companion object {
        private val ZIP_MAGIC = byteArrayOf('P'.code.toByte(), 'K'.code.toByte())
        private const val TEMP_EXPORT_PREFIX = "gift-export-"
        private const val TEMP_EXPORT_SUFFIX = ".gtlbackup"
    }
}