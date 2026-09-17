package com.gift.tolife.core.export

data class BackupV2Manifest(
    val formatVersion: Int = 2,
    val exportedAt: Long,
    val appVersion: String = "unknown",
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
    val tagIds: List<String> = emptyList(),
    val imageEntry: String? = null,
    val imageSha256: String? = null
)

/** 导出结果：用于给用户可核对的导出回执。 */
data class ExportResult(
    val totalCount: Int,
    val deletedCount: Int,
    val imageCount: Int,
    val missingImageCount: Int
)

/** 导入前的只读预检结果（不修改任何数据）。 */
data class ImportPreview(
    val formatVersion: Int,
    val isLegacy: Boolean,
    val entryCount: Int,
    val deletedCount: Int,
    val imageCount: Int,
    val appVersion: String?,
    val exportedAt: Long?
)

/** 导入/撤销的结果回执。 */
data class ImportResult(
    val totalCount: Int,
    val deletedCount: Int,
    val imageCount: Int,
    val snapshotCreated: Boolean,
    val droppedTagCount: Int = 0
)

/** 导入前自动快照信息（供 UI 展示撤销入口）。 */
data class SnapshotInfo(
    val createdAt: Long,
    val entryCount: Int
)

object BackupV2Config {
    const val MAX_BACKUP_FILE_BYTES = 2L * 1024 * 1024 * 1024 // 2 GiB
    const val MAX_ENTRY_COUNT = 100_000
    const val MAX_SINGLE_IMAGE_BYTES = 25L * 1024 * 1024 // 25 MiB
    const val MAX_TOTAL_IMAGE_BYTES = 10L * 1024 * 1024 * 1024 // 10 GiB
    const val MAX_MANIFEST_BYTES = 128L * 1024 * 1024 // 128 MiB，防 zip bomb 的 manifest
    const val MAX_LEGACY_JSON_BYTES = 256L * 1024 * 1024 // 256 MiB，V1 JSON 全量读入内存的上限
    const val MANIFEST_ENTRY_NAME = "manifest.json"
    const val IMAGE_DIR = "images/"
}
