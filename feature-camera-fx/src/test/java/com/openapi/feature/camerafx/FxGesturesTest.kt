package com.openapi.feature.camerafx

import org.junit.Assert.assertEquals
import org.junit.Test

class FxGesturesTest {

    private fun fullHand(block: (MutableList<Point2d>) -> Unit): List<Point2d> {
        val pts = MutableList(HAND_LANDMARK_COUNT) { Point2d(0.5f, 0.5f) }
        block(pts)
        return pts
    }

    @Test
    fun `点不足返回 NONE`() {
        assertEquals(HandGestureKind.NONE, classifyHandGesture(List(10) { Point2d(0f, 0f) }))
    }

    @Test
    fun `指尖远离腕判定为 OPEN`() {
        val open = fullHand { pts ->
            pts[0] = Point2d(0.5f, 0.7f)
            pts[4] = Point2d(0.30f, 0.25f)
            pts[8] = Point2d(0.48f, 0.20f)
            pts[12] = Point2d(0.5f, 0.18f)
            pts[16] = Point2d(0.58f, 0.2f)
            pts[20] = Point2d(0.65f, 0.22f)
        }
        assertEquals(HandGestureKind.OPEN, classifyHandGesture(open))
    }

    @Test
    fun `指尖贴近腕判定为 FIST`() {
        val fist = fullHand { pts ->
            pts[0] = Point2d(0.5f, 0.5f)
            for (i in 1 until HAND_LANDMARK_COUNT) pts[i] = Point2d(0.5f, 0.52f)
        }
        assertEquals(HandGestureKind.FIST, classifyHandGesture(fist))
    }

    @Test
    fun `拇指食指捏合判定为 PINCH`() {
        val pinch = fullHand { pts ->
            pts[0] = Point2d(0.5f, 0.6f)
            pts[4] = Point2d(0.48f, 0.45f)
            pts[8] = Point2d(0.50f, 0.46f)
            pts[12] = Point2d(0.5f, 0.25f)
            pts[16] = Point2d(0.52f, 0.25f)
            pts[20] = Point2d(0.54f, 0.25f)
        }
        assertEquals(HandGestureKind.PINCH, classifyHandGesture(pinch))
    }
}
