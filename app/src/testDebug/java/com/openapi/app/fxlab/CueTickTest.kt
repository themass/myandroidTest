package com.openapi.app.fxlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CueTickTest {

    @Test
    fun sameCueTwiceIncrementsSeqSoLayerRestarts() {
        val t = CueTick()
        t.fire("pat_head")
        val a = t.seq
        t.fire("pat_head")
        assertTrue(t.seq > a && t.gestureId == "pat_head")
    }

    @Test
    fun stopClearsGestureIdWithoutBumpingSeq() {
        val t = CueTick()
        t.fire("wave")
        val s = t.seq
        t.stop()
        assertEquals(null, t.gestureId)
        assertEquals(s, t.seq)
    }

    @Test
    fun teachCueForMatchesScript() {
        for (i in FxTeachingScript.steps.indices) {
            assertEquals(FxTeachingScript.steps[i].cue, teachCueFor(i))
        }
    }
}
