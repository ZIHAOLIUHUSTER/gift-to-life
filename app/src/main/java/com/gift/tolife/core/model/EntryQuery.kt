package com.gift.tolife.core.model

data class EntryQuery(
    val searchText: String = "",
    val startDate: Long? = null,
    val endDate: Long? = null,
    val hasImage: Boolean? = null,
    val selectedTags: List<TagType> = emptyList()
)
