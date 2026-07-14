package com.gift.tolife.core.export

data class BackupV2Manifest(
    val formatVersion: Int = 2,
    val exportedAt: Long,
    val appVersion: String = "1.5.0",
    val entryCount: Int,
    val entries: List<BackupV2Entry>
)

data class BackupV2Entry(
    val id: Long,
    val content: String,
    val type: String,
    val createdAt: Long,
    val updatedAt: Long = 0L,
    val summaryStart: Long? = null,
    val summaryEnd: Long? = null,
    val imageDescription: String? = null,
    val isDeleted: Boolean = false,
    val summaryModel: String? = null,
    val tags: List<String> = emptyList(),
    val imageEntry: String? = null,
    val imageSha256: String? = null
)

object BackupV2Config {
    const val MAX_BACKUP_FILE_BYTES = 2L * 1024 * 1024 * 1024 // 2 GiB
    const val MAX_ENTRY_COUNT = 100_000
    const val MAX_SINGLE_IMAGE_BYTES = 25L * 1024 * 1024 // 25 MiB
    const val MAX_TOTAL_IMAGE_BYTES = 10L * 1024 * 1024 * 1024 // 10 GiB
    const val MANIFEST_ENTRY_NAME = "manifest.json"
    const val IMAGE_DIR = "images/"
}
