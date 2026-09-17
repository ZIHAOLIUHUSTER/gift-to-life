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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
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

    /** 真实 App 版本，写进备份 manifest，便于归档追溯。 */
    private val appVersion: String by lazy {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"
    }

    // ---- 导出 ----

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
            V2ArchiveWriter(outputStream, appVersion = appVersion).use { writer ->
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

    // ---- 导入预检（只读，不改任何数据） ----

    /**
     * 读取备份内容概要，供 UI 在导入前向用户确认（尤其是"空备份"要二次确认）。
     * 只复制到临时文件做结构校验，不触碰数据库与图片目录。
     */
    suspend fun inspect(uri: Uri): ImportPreview = withContext(Dispatchers.IO) {
        val tempFile = copyToTempFile(uri)
        try {
            val firstBytes = readFirstBytes(tempFile)
            when {
                firstBytes.contentEquals(ZIP_MAGIC) -> {
                    val report = BackupArchiveVerifier.verify(tempFile)
                    val manifest = BackupArchiveVerifier.readManifest(tempFile)
                    ImportPreview(
                        formatVersion = manifest.formatVersion,
                        isLegacy = false,
                        entryCount = report.entryCount,
                        deletedCount = manifest.entries.count { it.isDeleted },
                        imageCount = report.imageCount,
                        appVersion = manifest.appVersion,
                        exportedAt = manifest.exportedAt
                    )
                }
                firstBytes[0] == '{'.code.toByte() -> {
                    val data = parseLegacyJson(tempFile)
                    ImportPreview(
                        formatVersion = data.version,
                        isLegacy = true,
                        entryCount = data.entries.size,
                        deletedCount = 0,
                        imageCount = data.entries.count { !it.imageBase64.isNullOrBlank() },
                        appVersion = null,
                        exportedAt = data.exportedAt
                    )
                }
                else -> throw IOException("不支持的备份格式")
            }
        } finally {
            tempFile.delete()
        }
    }

    // ---- 导入（替换式，导入前自动快照） ----

    /**
     * 用备份替换当前数据；替换前先把当前数据导出一份快照到 cacheDir（尽力而为），
     * 失败不会阻塞导入，但会在结果里说明。整个流程 NonCancellable。
     */
    suspend fun importWithSnapshot(uri: Uri): ImportResult = withContext(Dispatchers.IO + NonCancellable) {
        val tempFile = copyToTempFile(uri)
        try {
            val snapshotCreated = createSnapshot() != null
            val counts = importFromLocalFile(tempFile)
            ImportResult(
                totalCount = counts.total,
                deletedCount = counts.deleted,
                imageCount = counts.images,
                snapshotCreated = snapshotCreated
            )
        } finally {
            tempFile.delete()
        }
    }

    /** 最近一次导入前自动快照；没有则返回 null。 */
    suspend fun latestSnapshot(): SnapshotInfo? = withContext(Dispatchers.IO) {
        val file = listSnapshots().firstOrNull() ?: return@withContext null
        val manifest = try {
            BackupArchiveVerifier.readManifest(file)
        } catch (t: Exception) {
            return@withContext null
        }
        SnapshotInfo(createdAt = file.lastModified(), entryCount = manifest.entryCount)
    }

    /**
     * 撤销上次导入：先给"当前（要丢弃的）状态"再存一份快照（使撤销本身也可再撤销），
     * 然后用最近一份快照替换回来。
     */
    suspend fun undoLastImport(): ImportResult = withContext(Dispatchers.IO + NonCancellable) {
        val snapshot = listSnapshots().firstOrNull()
            ?: throw IOException("没有可撤销的导入快照")
        val snapshotCreated = createSnapshot(exclude = snapshot) != null
        val counts = importFromLocalFile(snapshot)
        ImportResult(
            totalCount = counts.total,
            deletedCount = counts.deleted,
            imageCount = counts.images,
            snapshotCreated = snapshotCreated
        )
    }

    private suspend fun importFromLocalFile(file: File): ImportCounts {
        val firstBytes = readFirstBytes(file)
        val counts = when {
            firstBytes.contentEquals(ZIP_MAGIC) -> importV2Safe(file)
            firstBytes[0] == '{'.code.toByte() -> importLegacyV1Safe(file)
            else -> throw IOException("不支持的备份格式")
        }
        // 导入成功后清空 streak 缓存
        settingsDataStore.updateStreak(0, 0)
        return counts
    }

    // ---- 快照管理 ----

    /** 生成当前数据快照；任何失败都返回 null（不阻塞导入）。 */
    private suspend fun createSnapshot(exclude: File? = null): File? {
        return try {
            val dir = snapshotDir()
            val file = File(dir, "$SNAPSHOT_PREFIX${System.currentTimeMillis()}.gtlbackup")
            writeArchive(file)
            BackupArchiveVerifier.verify(file)
            pruneSnapshots(exclude = exclude)
            file
        } catch (t: Exception) {
            null
        }
    }

    private fun snapshotDir(): File = File(context.cacheDir, SNAPSHOT_DIR).apply { mkdirs() }

    private fun listSnapshots(): List<File> = snapshotDir().listFiles()
        ?.filter { it.isFile && it.name.startsWith(SNAPSHOT_PREFIX) }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()

    private fun pruneSnapshots(exclude: File? = null) {
        listSnapshots().filter { it != exclude }.drop(MAX_SNAPSHOTS).forEach { it.delete() }
    }

    // ---- URI → 临时文件 ----

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

    private fun readFirstBytes(file: File): ByteArray {
        val buf = ByteArray(2)
        file.inputStream().use { stream ->
            if (stream.read(buf) != 2) throw IOException("文件太短")
        }
        return buf
    }

    /** 有上限地读入内存，避免超大 manifest / 旧版 JSON 直接 OOM。 */
    private fun readLimitedBytes(stream: InputStream, limit: Long, what: String): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) {
                throw IOException("$what 过大（上限 ${limit / (1024 * 1024)} MiB）")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun parseLegacyJson(file: File): ExportData {
        val json = file.inputStream().buffered().use { stream ->
            String(readLimitedBytes(stream, BackupV2Config.MAX_LEGACY_JSON_BYTES, "旧版备份"), Charsets.UTF_8)
        }
        val data = gson.fromJson(json, ExportData::class.java)
            ?: throw IOException("JSON 解析失败")
        @Suppress("SENSELESS_COMPARISON")
        if (data.entries == null) throw IOException("备份缺少 entries")
        return data
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

    // ---- V2 Safe Import (staging strategy) ----

    private suspend fun importV2Safe(tempFile: File): ImportCounts {
        val stagedImages = mutableMapOf<String, StagedImage>() // sourceEntryName -> StagedImage

        val stagingDir = File(context.cacheDir, "import-staging-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            java.util.zip.ZipFile(tempFile).use { zip ->
                // Phase 1: Parse manifest（有大小上限，防 zip bomb）
                val manifestEntry = zip.getEntry(BackupV2Config.MANIFEST_ENTRY_NAME)
                    ?: throw IOException("备份缺少 manifest.json")
                val manifest = zip.getInputStream(manifestEntry).use { stream ->
                    val json = String(
                        readLimitedBytes(stream, BackupV2Config.MAX_MANIFEST_BYTES, "manifest.json"),
                        Charsets.UTF_8
                    )
                    gson.fromJson(json, BackupV2Manifest::class.java)
                } ?: throw IOException("manifest.json 解析失败")
                require(manifest.formatVersion == 2) { "不支持的备份版本: ${manifest.formatVersion}" }
                require(manifest.entryCount == manifest.entries.size) { "条目数不匹配" }
                require(manifest.entries.size <= BackupV2Config.MAX_ENTRY_COUNT) { "条目数超过上限" }

                // Phase 2: Validate and stage images to cacheDir/import-staging
                // 配额按解压后的实际字节累计（ZIP 声明尺寸可伪造，不能作为总量依据）
                var totalActualImageBytes = 0L

                manifest.entries.forEach { e ->
                    e.imageEntry?.let { imageEntry ->
                        requireSafeImageEntry(imageEntry)
                        val zipEntry = zip.getEntry(imageEntry)
                            ?: throw IOException("清单声明了图片但 ZIP 中不存在: $imageEntry")
                        require(zipEntry.size <= BackupV2Config.MAX_SINGLE_IMAGE_BYTES) { "图片过大: $imageEntry" }

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
                        totalActualImageBytes += actualDecompressed
                        require(totalActualImageBytes <= BackupV2Config.MAX_TOTAL_IMAGE_BYTES) {
                            "图片总大小超过限制"
                        }
                        val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                        require(e.imageSha256 != null) { "图片缺少 SHA-256: $imageEntry" }
                        require(actualHash == e.imageSha256) { "图片哈希不匹配: $imageEntry" }

                        stagedImages[imageEntry] = StagedImage(stageFile, actualHash)
                    }
                }

                // Phase 3: Move staged images to final location (filesDir/images)
                // 一条目一份文件：多条目引用同一 imageEntry 时各自拿到独立副本，
                // 避免后续编辑/删除其中一条把另一条的图删掉。
                val imagesDir = File(context.filesDir, "images").apply { mkdirs() }
                val finalImagePaths = arrayOfNulls<String>(manifest.entries.size)
                try {
                    manifest.entries.forEachIndexed { index, e ->
                        val sourceName = e.imageEntry ?: return@forEachIndexed
                        val staged = stagedImages[sourceName]
                            ?: throw IOException("内部错误：图片未暂存: $sourceName")
                        val finalFile = File(imagesDir, "${UUID.randomUUID()}.webp")
                        staged.file.copyTo(finalFile, overwrite = true)
                        finalImagePaths[index] = finalFile.absolutePath
                    }
                } catch (e: Exception) {
                    // 清理已复制到正式目录的图片
                    finalImagePaths.filterNotNull().forEach { File(it).delete() }
                    throw e
                }

                // Phase 4: Build staged entries with final paths
                val stagedEntries = manifest.entries.mapIndexed { index, e ->
                    StagedImportEntry(
                        entry = Entry(
                            content = e.content,
                            type = try { EntryType.valueOf(e.type) } catch (_: Exception) { EntryType.NORMAL },
                            createdAt = e.createdAt,
                            updatedAt = e.updatedAt,
                            imagePath = finalImagePaths[index],
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
                    finalImagePaths.filterNotNull().forEach { File(it).delete() }
                    throw e
                }

                // Phase 6: Delete old images (only after successful commit)
                oldPaths.forEach { File(it).delete() }

                return ImportCounts(
                    total = stagedEntries.size,
                    deleted = stagedEntries.count { it.entry.isDeleted },
                    images = stagedEntries.count { it.entry.imagePath != null }
                )
            }
        } catch (e: Exception) {
            stagingDir.deleteRecursively()
            throw e
        } finally {
            if (stagingDir.exists()) stagingDir.deleteRecursively()
        }
    }

    // ---- V1 Safe Import (staging strategy) ----

    private suspend fun importLegacyV1Safe(tempFile: File): ImportCounts = withContext(Dispatchers.IO) {
        val data = parseLegacyJson(tempFile)
        require(data.entries.size <= BackupV2Config.MAX_ENTRY_COUNT) { "条目数超过上限" }

        val stagedEntries = mutableListOf<StagedImportEntry>()
        val imagesDir = File(context.filesDir, "images").apply { mkdirs() }
        var totalImageBytes = 0L
        val writtenImages = mutableListOf<String>()

        try {
            data.entries.forEach { e ->
                var imagePath: String? = null
                if (!e.imageBase64.isNullOrBlank()) {
                    val bytes = android.util.Base64.decode(e.imageBase64, android.util.Base64.NO_WRAP)
                    require(bytes.size.toLong() <= BackupV2Config.MAX_SINGLE_IMAGE_BYTES) { "图片过大" }
                    totalImageBytes += bytes.size
                    require(totalImageBytes <= BackupV2Config.MAX_TOTAL_IMAGE_BYTES) { "图片总大小超过限制" }
                    val file = File(imagesDir, "${UUID.randomUUID()}.webp")
                    file.writeBytes(bytes)
                    writtenImages.add(file.absolutePath)
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
        } catch (t: Throwable) {
            // 解析/解码中途失败也要回收已写入的图片，不留孤儿
            writtenImages.forEach { File(it).delete() }
            throw t
        }

        val oldPaths = entryDao.getAllImagePaths().toSet()
        try {
            database.replaceAll(stagedEntries)
        } catch (e: Exception) {
            // 回滚：删除本次导入的图片
            writtenImages.forEach { File(it).delete() }
            throw e
        }
        oldPaths.forEach { File(it).delete() }

        ImportCounts(
            total = stagedEntries.size,
            deleted = 0,
            images = writtenImages.size
        )
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

    private data class ImportCounts(val total: Int, val deleted: Int, val images: Int)

    companion object {
        private val ZIP_MAGIC = byteArrayOf('P'.code.toByte(), 'K'.code.toByte())
        private const val TEMP_EXPORT_PREFIX = "gift-export-"
        private const val TEMP_EXPORT_SUFFIX = ".gtlbackup"
        private const val SNAPSHOT_DIR = "import-snapshots"
        private const val SNAPSHOT_PREFIX = "snapshot-"
        private const val MAX_SNAPSHOTS = 3
    }
}
