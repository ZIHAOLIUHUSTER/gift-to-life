package com.gift.tolife.core.common

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val imagesDir: File get() = File(context.filesDir, "images").also { it.mkdirs() }

    fun delete(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val file = File(path)
        return if (file.exists() && file.parent == imagesDir.absolutePath) {
            file.delete()
        } else false
    }

    fun deleteAll(paths: Collection<String>) {
        paths.forEach { delete(it) }
    }

    fun listManagedPaths(): Set<String> {
        return imagesDir.listFiles()?.map { it.absolutePath }?.toSet() ?: emptySet()
    }

    /**
     * 删除未被数据库引用的图片。
     *
     * 只删 [minFileAgeMs] 之前写入的文件：导入时图片先落盘（phase 3）再提交数据库（phase 5），
     * 启动清扫与导入交迭时年轻的文件不应当作孤儿删除。
     */
    fun removeOrphans(referencedPaths: Set<String>, minFileAgeMs: Long = ORPHAN_MIN_AGE_MS): Int {
        val managed = listManagedPaths()
        val now = System.currentTimeMillis()
        var removed = 0
        managed.forEach { path ->
            if (path in referencedPaths) return@forEach
            if (now - File(path).lastModified() < minFileAgeMs) return@forEach
            if (delete(path)) removed++
        }
        return removed
    }

    /**
     * 清理上次异常退出遗留的临时文件：导入 staging 目录、导入临时文件、导出临时备份。
     */
    fun cleanupTempFiles() {
        context.cacheDir.listFiles()?.forEach { file ->
            val name = file.name
            when {
                file.isDirectory && name.startsWith("import-staging-") -> file.deleteRecursively()
                file.isFile && name.startsWith("gift-import-") -> file.delete()
                file.isFile && name.startsWith("gift-export-") -> file.delete()
            }
        }
    }

    companion object {
        /** 孤儿图片最小存活时间（10 分钟）。 */
        const val ORPHAN_MIN_AGE_MS = 10 * 60 * 1000L
    }
}
