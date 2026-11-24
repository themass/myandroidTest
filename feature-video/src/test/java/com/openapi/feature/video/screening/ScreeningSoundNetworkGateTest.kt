package com.openapi.feature.video.screening

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreeningSoundNetworkGateTest {

    @Test
    fun `prompts on mobile data unless sound preference or session override allows playback`() {
        assertTrue(shouldPromptForSoundNetwork(isWifi = false, soundSwitch = false, sessionOverride = false))
        assertFalse(shouldPromptForSoundNetwork(isWifi = true, soundSwitch = false, sessionOverride = false))
        assertFalse(shouldPromptForSoundNetwork(isWifi = false, soundSwitch = true, sessionOverride = false))
        assertFalse(shouldPromptForSoundNetwork(isWifi = false, soundSwitch = false, sessionOverride = true))
    }
}
