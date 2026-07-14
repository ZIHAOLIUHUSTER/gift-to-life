package com.gift.tolife.core.common

import android.net.Uri
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

data class SharedContent(val text: String?, val imageUri: Uri?)

object ShareReceiver {
    private val eventsChannel = Channel<SharedContent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    fun publish(content: SharedContent) {
        eventsChannel.trySend(content)
    }
}
