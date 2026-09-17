package com.gift.tolife.core.export

import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * 备份归档的结构性自检（导出落盘后、写入目标文件前调用）：
 * manifest 可解析、版本与条目数一致、清单声明的每张图片都存在于 ZIP 中且带 SHA-256。
 *
 * 纯 JVM 实现（不依赖 Android），只读不修改文件；校验失败抛 [IOException]。
 */
object BackupArchiveVerifier {

    data class Report(val entryCount: Int, val imageCount: Int)

    fun verify(file: File): Report {
        if (!file.isFile) throw IOException("备份文件不存在")
        return ZipFile(file).use { zip ->
            val manifestEntry = zip.getEntry(BackupV2Config.MANIFEST_ENTRY_NAME)
                ?: throw IOException("备份缺少 manifest.json")
            val json = zip.getInputStream(manifestEntry).use { String(it.readBytes()) }
            val parsed: BackupV2Manifest? = try {
                Gson().fromJson(json, BackupV2Manifest::class.java)
            } catch (t: Throwable) {
                null
            }
            val manifest = parsed ?: throw IOException("manifest.json 解析失败")
            if (manifest.formatVersion != 2) {
                throw IOException("不支持的备份版本: ${manifest.formatVersion}")
            }
            @Suppress("SENSELESS_COMPARISON")
            if (manifest.entries == null) throw IOException("manifest 缺少 entries")
            if (manifest.entryCount != manifest.entries.size) throw IOException("条目数不匹配")
            if (manifest.entries.size > BackupV2Config.MAX_ENTRY_COUNT) throw IOException("条目数超过上限")

            var imageCount = 0
            manifest.entries.forEach { entry ->
                val name = entry.imageEntry ?: return@forEach
                if (zip.getEntry(name) == null) {
                    throw IOException("清单声明了图片但 ZIP 中不存在: $name")
                }
                if (entry.imageSha256.isNullOrBlank()) {
                    throw IOException("图片缺少 SHA-256: $name")
                }
                imageCount++
            }
            Report(entryCount = manifest.entries.size, imageCount = imageCount)
        }
    }
}
