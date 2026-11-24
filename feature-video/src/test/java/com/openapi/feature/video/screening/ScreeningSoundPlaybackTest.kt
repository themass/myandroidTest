package com.openapi.feature.video.screening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreeningSoundPlaybackTest {

    @Test
    fun `单曲循环完播后仍选择当前曲目`() {
        assertEquals(2, nextSoundTrackIndex(SoundPlaybackMode.SINGLE, currentIndex = 2, trackCount = 4))
    }

    @Test
    fun `列表循环完播后从末尾回到第一首`() {
        assertEquals(0, nextSoundTrackIndex(SoundPlaybackMode.LIST, currentIndex = 3, trackCount = 4))
    }

    @Test
    fun `顺序播放完播到列表末尾后停止`() {
        assertNull(nextSoundTrackIndex(SoundPlaybackMode.SEQUENTIAL, currentIndex = 3, trackCount = 4))
    }

    @Test
    fun `随机播放只从非当前曲目中选择`() {
        var candidates: List<Int>? = null
        val selected = nextSoundTrackIndex(
            SoundPlaybackMode.SHUFFLE,
            currentIndex = 2,
            trackCount = 4,
            chooseRandom = { choices -> candidates = choices; choices.first() },
        )
        assertEquals(listOf(0, 1, 3), candidates)
        assertEquals(0, selected)
    }

    @Test
    fun `单曲随机播放不会制造无效索引`() {
        assertEquals(0, nextSoundTrackIndex(SoundPlaybackMode.SHUFFLE, currentIndex = 0, trackCount = 1))
    }

    @Test
    fun `空曲目列表没有下一首`() {
        assertNull(nextSoundTrackIndex(SoundPlaybackMode.LIST, currentIndex = -1, trackCount = 0))
    }

    @Test
    fun `播放模式从偏好恢复且未知值回退到列表循环`() {
        assertEquals(SoundPlaybackMode.SHUFFLE, soundPlaybackMode("SHUFFLE"))
        assertEquals(SoundPlaybackMode.LIST, soundPlaybackMode(null))
        assertEquals(SoundPlaybackMode.LIST, soundPlaybackMode("removed_mode"))
    }

    @Test
    fun `手动下一首按模式选择并在已加载队列末尾留给分页`() {
        assertEquals(2, manualNextSoundTrackIndex(SoundPlaybackMode.SINGLE, 1, 4))
        assertEquals(0, manualNextSoundTrackIndex(SoundPlaybackMode.LIST, 3, 4))
        assertNull(manualNextSoundTrackIndex(SoundPlaybackMode.SEQUENTIAL, 3, 4))
    }

    @Test
    fun `手动随机下一首排除当前曲目`() {
        var candidates: List<Int>? = null
        val selected = manualNextSoundTrackIndex(SoundPlaybackMode.SHUFFLE, 2, 4) { choices ->
            candidates = choices
            choices.first()
        }
        assertEquals(listOf(0, 1, 3), candidates)
        assertEquals(0, selected)
    }

    @Test
    fun `手动上一首沿同一队列退一首并在队首停止`() {
        assertEquals(1, previousSoundTrackIndex(currentIndex = 2, trackCount = 4))
        assertNull(previousSoundTrackIndex(currentIndex = 0, trackCount = 4))
    }

    @Test
    fun `播放倍速限制为旧服务支持的零点二五到三倍`() {
        assertEquals(0.25f, clampSoundPlaybackSpeed(0.1f), 0.001f)
        assertEquals(1.75f, clampSoundPlaybackSpeed(1.75f), 0.001f)
        assertEquals(3f, clampSoundPlaybackSpeed(3.5f), 0.001f)
    }
}
