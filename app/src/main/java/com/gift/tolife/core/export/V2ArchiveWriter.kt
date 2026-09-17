package com.gift.tolife.core.export

import com.gift.tolife.core.model.Entry
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 把条目与图片写成 v2 备份 ZIP（manifest.json 最后写入，图片流式写入）。
 *
 * 纯 JVM 实现（不依赖 Android），由 [ExportImportManager] 按 DAO 分页喂入数据，
 * 便于单元测试覆盖备份格式与边界情况。
 */
class V2ArchiveWriter(
    output: OutputStream,
    private val appVersion: String = "unknown",
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
) : Closeable {

    private val zip = ZipOutputStream(BufferedOutputStream(output))
    private val manifestEntries = ArrayList<BackupV2Entry>()
    private val usedImageNames = HashSet<String>()
    private var deletedCount = 0
    private var imageCount = 0
    private var missingImageCount = 0

    /**
     * 追加一条记录（可重复调用，最后必须调用 [finish]）。
     *
     * @param imageFile 条目引用的图片文件；null 表示无图；文件不存在时以无图写入并计入缺失统计。
     */
    fun addEntry(entry: Entry, tags: List<String>, imageFile: File?) {
        var imageEntry: String? = null
        var imageSha256: String? = null
        if (imageFile != null) {
            if (imageFile.isFile) {
                val name = uniqueImageName(imageFile.name)
                imageEntry = "${BackupV2Config.IMAGE_DIR}$name"
                imageSha256 = writeImage(imageEntry, imageFile)
                imageCount++
            } else {
                missingImageCount++
            }
        }
        if (entry.isDeleted) deletedCount++
        manifestEntries.add(
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
        )
    }

    /** 写入 manifest 并结束归档，返回导出回执。调用方负责 [close]。 */
    fun finish(): ExportResult {
        val manifest = BackupV2Manifest(
            exportedAt = System.currentTimeMillis(),
            appVersion = appVersion,
            entryCount = manifestEntries.size,
            entries = manifestEntries
        )
        zip.putNextEntry(ZipEntry(BackupV2Config.MANIFEST_ENTRY_NAME))
        zip.write(gson.toJson(manifest).toByteArray())
        zip.closeEntry()
        return ExportResult(
            totalCount = manifestEntries.size,
            deletedCount = deletedCount,
            imageCount = imageCount,
            missingImageCount = missingImageCount
        )
    }

    override fun close() {
        zip.close()
    }

    /**
     * 两条目共用同一个图片文件时（例如导入过异常备份）加数字后缀，
     * 避免 ZipOutputStream 因重名条目抛 ZipException 导致整次导出失败。
     */
    private fun uniqueImageName(original: String): String {
        if (usedImageNames.add(original)) return original
        val dot = original.lastIndexOf('.')
        val base = if (dot > 0) original.substring(0, dot) else original
        val ext = if (dot > 0) original.substring(dot) else ""
        var index = 2
        while (true) {
            val candidate = "${base}_$index$ext"
            if (usedImageNames.add(candidate)) return candidate
            index++
        }
    }

    /** 边写边算 SHA-256，保证清单里的哈希与 ZIP 内实际字节一致。 */
    private fun writeImage(entryName: String, file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                zip.write(buffer, 0, read)
            }
        }
        zip.closeEntry()
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
