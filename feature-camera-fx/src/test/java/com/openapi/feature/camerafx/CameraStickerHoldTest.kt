package com.openapi.feature.camerafx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraStickerHoldTest {

    @Test
    fun `首帧未检测到直接 Off 不画`() {
        val t = StickerHoldTracker(graceFrames = 3)
        assertTrue(t.advance(hasAnchor = false) is StickerTrack.Off)
    }

    @Test
    fun `单帧漏检走 Held 不闪`() {
        val t = StickerHoldTracker(graceFrames = 3)
        assertTrue(t.advance(hasAnchor = true) is StickerTrack.Anchored)
        val after = t.advance(hasAnchor = false)
        assertTrue(after is StickerTrack.Held)
        assertEquals(1, (after as StickerTrack.Held).missedFrames)
    }

    @Test
    fun `宽限内多帧连续 Held`() {
        val t = StickerHoldTracker(graceFrames = 3)
        t.advance(hasAnchor = true)
        repeat(3) { i ->
            val s = t.advance(hasAnchor = false)
            assertTrue(s is StickerTrack.Held)
            assertEquals(i + 1, (s as StickerTrack.Held).missedFrames)
        }
    }

    @Test
    fun `超过宽限转 Off`() {
        val t = StickerHoldTracker(graceFrames = 2)
        t.advance(hasAnchor = true)
        t.advance(hasAnchor = false)
        t.advance(hasAnchor = false)
        assertTrue(t.advance(hasAnchor = false) is StickerTrack.Off)
    }

    @Test
    fun `重新检测到计数归零`() {
        val t = StickerHoldTracker(graceFrames = 2)
        t.advance(hasAnchor = true)
        t.advance(hasAnchor = false) // Held 1
        assertTrue(t.advance(hasAnchor = true) is StickerTrack.Anchored)
        t.advance(hasAnchor = false)
        val s = t.advance(hasAnchor = false)
        assertTrue(s is StickerTrack.Held)
        assertEquals(2, (s as StickerTrack.Held).missedFrames)
    }

    @Test
    fun `graceFrames 0 不容错`() {
        val t = StickerHoldTracker(graceFrames = 0)
        t.advance(hasAnchor = true)
        assertTrue(t.advance(hasAnchor = false) is StickerTrack.Off)
    }

    @Test
    fun `reset 后未锚定状态干净`() {
        val t = StickerHoldTracker(graceFrames = 3)
        t.advance(hasAnchor = true)
        t.advance(hasAnchor = false)
        t.reset()
        assertTrue(t.advance(hasAnchor = false) is StickerTrack.Off)
    }
}
