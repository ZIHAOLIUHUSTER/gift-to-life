package com.gift.tolife.core.database

import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.TagType

data class StagedImportEntry(
    val entry: Entry,
    val tags: List<TagType>
)
