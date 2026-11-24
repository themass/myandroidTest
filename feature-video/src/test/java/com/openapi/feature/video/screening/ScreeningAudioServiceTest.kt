package com.openapi.feature.video.screening

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreeningAudioServiceTest {

    @Test
    fun `notification media item carries track title and audio url`() {
        val item = screeningAudioMediaItem(
            title = "Rain on the roof",
            subtitle = "Ambient sounds",
            fileUrl = "https://audio.example.test/rain.mp3",
        )

        assertEquals("Rain on the roof", item.mediaMetadata.title)
        assertEquals("Ambient sounds", item.mediaMetadata.artist)
        assertEquals("https://audio.example.test/rain.mp3", item.mediaId)
    }
}
