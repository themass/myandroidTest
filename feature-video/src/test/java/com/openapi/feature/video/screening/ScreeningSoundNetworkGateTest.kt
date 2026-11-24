package com.openapi.feature.video.screening

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreeningSoundNetworkGateTest {

    @Test
    fun `sound preference defaults to enabled as in the settings screen`() {
        assertTrue(SOUND_SWITCH_DEFAULT_ENABLED)
    }

    @Test
    fun `only interrupts active playback when mobile playback is not allowed`() {
        assertTrue(shouldPromptForSoundNetwork(isWifi = false, soundSwitch = false, sessionOverride = false, isPlaying = true))
        assertFalse(shouldPromptForSoundNetwork(isWifi = false, soundSwitch = false, sessionOverride = false, isPlaying = false))
        assertFalse(shouldPromptForSoundNetwork(isWifi = true, soundSwitch = false, sessionOverride = false, isPlaying = true))
        assertFalse(shouldPromptForSoundNetwork(isWifi = false, soundSwitch = true, sessionOverride = false, isPlaying = true))
        assertFalse(shouldPromptForSoundNetwork(isWifi = false, soundSwitch = false, sessionOverride = true, isPlaying = true))
    }
}
