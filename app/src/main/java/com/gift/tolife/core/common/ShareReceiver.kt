package com.gift.tolife.core.common

import android.net.Uri
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.atomic.AtomicLong

data class SharedContent(val text: String?, val imageUri: Uri?)

object ShareReceiver {
    private val eventsChannel = Channel<SharedContent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()
    private val nextRequest = AtomicLong()
    private val _navigationRequests = MutableStateFlow<Long?>(null)
    val navigationRequests = _navigationRequests.asStateFlow()

    fun publish(content: SharedContent) {
        if (eventsChannel.trySend(content).isSuccess) {
            // 内容仍由记录页消费；导航单独保留，冷启动和热启动都能接住。
            _navigationRequests.value = nextRequest.incrementAndGet()
        }
    }

    fun consumeNavigationRequest(id: Long) {
        _navigationRequests.compareAndSet(id, null)
    }
}
