package com.openapi.feature.video.screening

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreeningSoundSubtitleTest {

    @Test
    fun `sound subtitle uses active reprocessable media url and falls back to selected track`() {
        assertEquals(
            "https://audio.example.test/current.mp3",
            screeningSoundRealtimeUrl(
                currentMediaUrl = "https://audio.example.test/current.mp3",
                selectedTrackUrl = "https://audio.example.test/selected.mp3",
            ),
        )
        assertEquals(
            "https://audio.example.test/selected.mp3",
            screeningSoundRealtimeUrl(currentMediaUrl = null, selectedTrackUrl = "https://audio.example.test/selected.mp3"),
        )
        assertEquals(null, screeningSoundRealtimeUrl("content://media/audio/1", null))
    }

    @Test
    fun `subtitle extraction prefers the actual player url then selected sound track`() {
        assertEquals(
            "https://audio.example.test/current.mp3",
            screeningSubtitleMediaUrl(
                currentMediaUrl = "https://audio.example.test/current.mp3",
                selectedTrackUrl = "https://audio.example.test/selected.mp3",
            ),
        )
        assertEquals(
            "https://audio.example.test/selected.mp3",
            screeningSubtitleMediaUrl(currentMediaUrl = null, selectedTrackUrl = "https://audio.example.test/selected.mp3"),
        )
    }
}
