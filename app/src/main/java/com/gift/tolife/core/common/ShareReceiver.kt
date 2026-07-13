package com.gift.tolife.core.common

import android.net.Uri

object ShareReceiver {
    @Volatile
    var pendingText: String? = null

    @Volatile
    var pendingImageUri: Uri? = null

    fun hasPending(): Boolean = pendingText != null || pendingImageUri != null

    fun consume(): Pair<String?, Uri?> {
        val text = pendingText
        val image = pendingImageUri
        pendingText = null
        pendingImageUri = null
        return text to image
    }
}
