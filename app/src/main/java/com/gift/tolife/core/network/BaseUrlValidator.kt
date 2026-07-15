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
        require(parsed.isHttps) { "API 地址必须使用 HTTPS" }
        require(parsed.username.isEmpty() && parsed.password.isEmpty()) { "API 地址不能包含用户信息" }
        require(parsed.query == null && parsed.fragment == null) { "API 地址不能包含 query 或 fragment" }
        val base = parsed.toString().trimEnd('/')
            .removeSuffix("/v1/chat/completions")
            .trimEnd('/')
        return "$base/"
    }
}
