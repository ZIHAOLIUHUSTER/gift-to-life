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

    fun removeOrphans(referencedPaths: Set<String>): Int {
        val managed = listManagedPaths()
        var removed = 0
        managed.forEach { path ->
            if (path !in referencedPaths) {
                if (delete(path)) removed++
            }
        }
        return removed
    }

    /**
     * Clean up stale import-staging directories left by crashes during import.
     */
    fun cleanupStagingDirs() {
        context.cacheDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory && dir.name.startsWith("import-staging-")) {
                dir.deleteRecursively()
            }
        }
    }
}
