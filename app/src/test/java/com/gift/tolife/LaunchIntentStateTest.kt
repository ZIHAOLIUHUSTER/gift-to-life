package com.gift.tolife

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchIntentStateTest {
    @Test
    fun initialDeliveryIsClaimedOnlyOnceAcrossConfigurationChanges() {
        val retainedState = LaunchIntentState()
        assertTrue(retainedState.claimInitialIntent())
        assertFalse(retainedState.claimInitialIntent())
        assertFalse(retainedState.claimInitialIntent())
    }

    @Test
    fun recreatedProcessCanReceiveItsLaunchIntentAgain() {
        val previousProcess = LaunchIntentState()
        assertTrue(previousProcess.claimInitialIntent())
        val recreatedProcess = LaunchIntentState()
        assertTrue(recreatedProcess.claimInitialIntent())
    }
}
