package com.gift.tolife.core.export

data class ExportData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val entries: List<ExportEntry>
)

data class ExportEntry(
    val content: String,
    val type: String,
    val createdAt: Long,
    val tags: List<String>,
    val imageFileName: String? = null,
    val imageDescription: String? = null
)
