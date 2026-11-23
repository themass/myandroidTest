package com.openapi.feature.camerafx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraGestureCueTest {

    @Test
    fun mapsToStageVocabulary() {
        assertEquals("wave", CameraGestureCue.stageGestureId(HandGestureKind.OPEN))
        assertEquals("pat_head", CameraGestureCue.stageGestureId(HandGestureKind.PINCH))
        assertNull(CameraGestureCue.stageGestureId(HandGestureKind.FIST))
        assertNull(CameraGestureCue.stageGestureId(HandGestureKind.NONE))
        assertNull(CameraGestureCue.stageGestureId(HandGestureKind.UNKNOWN))
    }

    @Test
    fun stableGestureEmitsOnce() {
        val edge = StageGestureEdge(holdFrames = 2, cooldownFrames = 3)
        assertNull(edge.update(HandGestureKind.OPEN)) // run 1
        assertEquals("wave", edge.update(HandGestureKind.OPEN)) // run 2 → 发一次
        assertNull(edge.update(HandGestureKind.OPEN)) // 保持不再发
        assertNull(edge.update(HandGestureKind.OPEN))
    }

    @Test
    fun cooldownSuppressesImmediateDifferentCue() {
        val edge = StageGestureEdge(holdFrames = 2, cooldownFrames = 3)
        edge.update(HandGestureKind.OPEN)
        assertEquals("wave", edge.update(HandGestureKind.OPEN)) // f2，冷却到 f5
        assertNull(edge.update(HandGestureKind.PINCH)) // f3 run1
        assertNull(edge.update(HandGestureKind.PINCH)) // f4 稳定但在冷却内 → 压掉
        assertEquals("pat_head", edge.update(HandGestureKind.PINCH)) // f5 出冷却 → 发
    }

    @Test
    fun releaseThenSameGestureFiresAgain() {
        val edge = StageGestureEdge(holdFrames = 2, cooldownFrames = 3)
        edge.update(HandGestureKind.OPEN)
        assertEquals("wave", edge.update(HandGestureKind.OPEN))
        // 收手（NONE 稳定）→ 再举手可再发
        edge.update(HandGestureKind.NONE)
        edge.update(HandGestureKind.NONE)
        edge.update(HandGestureKind.OPEN)
        assertEquals("wave", edge.update(HandGestureKind.OPEN))
    }

    @Test
    fun flickerUnderHoldDoesNotEmit() {
        val edge = StageGestureEdge(holdFrames = 3, cooldownFrames = 5)
        edge.update(HandGestureKind.OPEN)
        edge.update(HandGestureKind.NONE)
        edge.update(HandGestureKind.OPEN)
        assertNull(edge.update(HandGestureKind.NONE))
    }

    @Test
    fun fistNeverEmits() {
        val edge = StageGestureEdge(holdFrames = 1, cooldownFrames = 0)
        repeat(5) { assertNull(edge.update(HandGestureKind.FIST)) }
    }

    @Test
    fun resetReEnablesSameGesture() {
        val edge = StageGestureEdge(holdFrames = 1, cooldownFrames = 0)
        assertEquals("wave", edge.update(HandGestureKind.OPEN))
        assertNull(edge.update(HandGestureKind.OPEN))
        edge.reset()
        assertEquals("wave", edge.update(HandGestureKind.OPEN))
    }
}
