package com.openapi.feature.video.screening

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreeningAudioConnectionTest {

    @Test
    fun `controller connection failure is represented as recoverable error state`() {
        assertEquals(SoundPlayerConnectionState.FAILED, soundPlayerConnectionState(hasPlayer = false, failed = true))
    }

    @Test
    fun `controller connection remains pending until it succeeds or fails`() {
        assertEquals(SoundPlayerConnectionState.CONNECTING, soundPlayerConnectionState(hasPlayer = false, failed = false))
    }

    @Test
    fun `connected controller state takes precedence over prior failure`() {
        assertEquals(SoundPlayerConnectionState.CONNECTED, soundPlayerConnectionState(hasPlayer = true, failed = true))
    }
}
