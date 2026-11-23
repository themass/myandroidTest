package com.openapi.feature.companion.fx

import org.junit.Assert.assertEquals
import org.junit.Test

/** 去重纯函数：同 assetId 400ms 内合并；边界严格小于。 */
class StageFxDedupeTest {

    @Test
    fun firstSeenAlwaysPasses() {
        assertEquals(DedupeAction.PASS, dedupe(null, 1_000_000L))
    }

    @Test
    fun withinWindowDrops() {
        val t0 = 1_000_000_000L
        val within = t0 + (FX_DEDUPE_WINDOW_MS - 1) * 1_000_000L
        assertEquals(DedupeAction.DROP, dedupe(t0, within))
    }

    @Test
    fun exactlyWindowPasses() {
        val t0 = 1_000_000_000L
        val edge = t0 + FX_DEDUPE_WINDOW_MS * 1_000_000L
        assertEquals(DedupeAction.PASS, dedupe(t0, edge))
    }

    @Test
    fun beyondWindowPasses() {
        val t0 = 1_000_000_000L
        val later = t0 + (FX_DEDUPE_WINDOW_MS + 50) * 1_000_000L
        assertEquals(DedupeAction.PASS, dedupe(t0, later))
    }
}
