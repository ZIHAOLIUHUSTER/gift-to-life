package com.gift.tolife.core.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object BaseUrlValidator {
    fun normalize(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        var withScheme = trimmed
        if (!withScheme.startsWith("http://") && !withScheme.startsWith("https://")) {
            withScheme = "https://$withScheme"
        }
        val parsed = withScheme.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("API 地址格式无效")
        val base = parsed.toString().trimEnd('/')
            .removeSuffix("/v1/chat/completions")
            .trimEnd('/')
        return "$base/"
    }
}
