package com.openapi.feature.companion.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FxAnchorsTest {

    @Test
    fun fullCoversViewport() {
        assertEquals(1f, FxAnchors.widthFraction(StageEffect.Anchor.FULL))
        val p = FxAnchors.point(StageEffect.Anchor.FULL)
        assertEquals(0.5f, p.x)
        assertEquals(0.5f, p.y)
    }

    @Test
    fun headAboveBody() {
        val head = FxAnchors.point(StageEffect.Anchor.HEAD)
        val body = FxAnchors.point(StageEffect.Anchor.BODY)
        assertTrue(head.y < body.y)
        assertTrue(head.y < 0.42f) // 与 stageTapRegion 头/身分界一致偏上
    }

    @Test
    fun anchorFractionsBetweenHalfAndOne() {
        for (a in listOf(StageEffect.Anchor.HEAD, StageEffect.Anchor.BODY, StageEffect.Anchor.HAND)) {
            val f = FxAnchors.widthFraction(a)
            assertTrue("$a fraction $f", f in 0.5f..0.9f)
        }
    }
}
