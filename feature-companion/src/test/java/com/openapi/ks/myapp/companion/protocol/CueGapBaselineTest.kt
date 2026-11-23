package com.openapi.ks.myapp.companion.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reconnect must baseline lifetime [ProtocolReceiver.State.gaps], not zero it.
 * Zeroing replays historical gaps into cueGapStreak → false L1.
 */
class CueGapBaselineTest {

    @Test
    fun `reconnect baselines historical gaps so first clean cue does not replay`() {
        val b = CueGapBaseline()
        // lifetime gaps survived forgetAll (e.g. 5 from previous connection)
        b.baseline(5)
        assertEquals(0, b.observe(5)) // first post-reconnect cue, no new gap
        assertEquals(1, b.observe(6)) // real new gap only
        assertEquals(0, b.observe(6)) // same again → no growth
    }

    @Test
    fun `zero baseline would replay history — the bug we refuse`() {
        val b = CueGapBaseline()
        // Simulate the old bug: lastObservedGaps = 0 after reconnect while gaps==5
        // (do NOT call baseline). First observe must NOT be treated as correct.
        // With proper API we always baseline; this documents the delta math.
        assertEquals(5, CueGapBaseline.delta(lastObserved = 0, gapsNow = 5))
        assertEquals(0, CueGapBaseline.delta(lastObserved = 5, gapsNow = 5))
    }
}
