package com.openapi.feature.video.screening

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreeningSoundLyricsTest {

    @Test
    fun `server lyrics wins over realtime whenever a server track is loaded`() {
        assertEquals(
            SoundCaptionSource.SERVER,
            soundCaptionSource(serverTrackLoaded = true, realtimeConfigured = true, urlUsable = true),
        )
        assertEquals(
            SoundCaptionSource.SERVER,
            soundCaptionSource(serverTrackLoaded = true, realtimeConfigured = false, urlUsable = false),
        )
    }

    @Test
    fun `realtime is the fallback when there is no server track`() {
        assertEquals(
            SoundCaptionSource.REALTIME,
            soundCaptionSource(serverTrackLoaded = false, realtimeConfigured = true, urlUsable = true),
        )
    }

    @Test
    fun `no caption source when realtime cannot run`() {
        assertEquals(
            SoundCaptionSource.NONE,
            soundCaptionSource(serverTrackLoaded = false, realtimeConfigured = false, urlUsable = true),
        )
        assertEquals(
            SoundCaptionSource.NONE,
            soundCaptionSource(serverTrackLoaded = false, realtimeConfigured = true, urlUsable = false),
        )
        assertEquals(
            SoundCaptionSource.NONE,
            soundCaptionSource(serverTrackLoaded = false, realtimeConfigured = false, urlUsable = false),
        )
    }

    @Test
    fun `sound list entry keeps the legacy key and carries the new server id`() {
        val vo = SoundItemsVo().apply {
            id = 42
            name = "Rain on the roof"
            file = "https://audio.example.test/rain.mp3"
            fileDate = "2026-11-01"
        }
        val entry = vo.toSoundListEntry(3)
        assertEquals("sound:item:3:https://audio.example.test/rain.mp3", entry.key)
        assertEquals(42L, entry.serverId)
    }

    @Test
    fun `sound list entry from an old server without id stays lyrics-unusable`() {
        val vo = SoundItemsVo().apply {
            name = "Old item"
            file = "https://audio.example.test/old.mp3"
        }
        assertEquals(null, vo.toSoundListEntry(0).serverId)
    }

    @Test
    fun `track ref carries the server id through to the lyrics lookup`() {
        val entry = RemoteEntry(
            key = "sound:item:0:https://audio.example.test/a.mp3",
            title = "a",
            description = "",
            imageUrl = null,
            ratio = 1f,
            actionUrl = "https://audio.example.test/a.mp3",
            needsLazyUrl = false,
            channelParam = null,
            param = null,
            isImageCard = false,
            serverId = 42L,
        )
        assertEquals(42L, entry.soundTrackRef(0)?.serverId)
    }
}
