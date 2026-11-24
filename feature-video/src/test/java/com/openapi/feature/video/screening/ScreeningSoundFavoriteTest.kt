package com.openapi.feature.video.screening

import com.openapi.core.config.Constants
import com.openapi.core.model.RecommendVo
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreeningSoundFavoriteTest {

    @Test
    fun `sound favorite uses the legacy SOUND type and track url`() {
        val favorite = SoundTrackRef(
            index = 0,
            title = "Rain on the roof",
            fileUrl = "https://audio.example.test/rain.mp3",
            subtitle = "Ambient sounds",
        ).toSoundFavorite()

        assertEquals(Constants.FavoriteType.SOUND, favorite.type)
        assertEquals("Rain on the roof", favorite.name)
        assertEquals("https://audio.example.test/rain.mp3", favorite.itemUrl)
        assertEquals("Rain on the roof", (favorite.o as RecommendVo).title)
    }
}
