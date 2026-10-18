package com.gift.tolife.core.common

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ShareReceiverTest {
    @Test
    fun shareBeforeNavigationStartsKeepsBothContentAndNavigation() = runTest {
        val content = SharedContent("带进来的一句话", null)
        ShareReceiver.publish(content)
        val request = ShareReceiver.navigationRequests.value
        assertNotNull(request)

        ShareReceiver.consumeNavigationRequest(request!!)
        assertNull(ShareReceiver.navigationRequests.value)
        assertEquals(content, ShareReceiver.events.first())
    }

    @Test
    fun consumingOlderNavigationDoesNotLoseTheNewerShare() = runTest {
        val first = SharedContent("第一句", null)
        val second = SharedContent("第二句", null)
        ShareReceiver.publish(first)
        val oldRequest = ShareReceiver.navigationRequests.value!!
        ShareReceiver.publish(second)
        val newRequest = ShareReceiver.navigationRequests.value!!
        assertNotEquals(oldRequest, newRequest)

        ShareReceiver.consumeNavigationRequest(oldRequest)
        assertEquals(newRequest, ShareReceiver.navigationRequests.value)
        assertEquals(first, ShareReceiver.events.first())
        assertEquals(second, ShareReceiver.events.first())
        ShareReceiver.consumeNavigationRequest(newRequest)
        assertNull(ShareReceiver.navigationRequests.value)
    }

    @Test
    fun widgetFocusRequestStillNavigatesWithoutAddingContent() = runTest {
        val focusOnly = SharedContent(null, null)
        ShareReceiver.publish(focusOnly)
        val request = ShareReceiver.navigationRequests.value!!
        assertEquals(focusOnly, ShareReceiver.events.first())
        ShareReceiver.consumeNavigationRequest(request)
        assertNull(ShareReceiver.navigationRequests.value)
    }
}
