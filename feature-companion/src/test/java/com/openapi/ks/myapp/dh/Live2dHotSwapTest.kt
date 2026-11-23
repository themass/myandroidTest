package com.openapi.ks.myapp.dh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Live2dHotSwapTest {

    @Test
    fun `hot swap when live2d view up and model changes`() {
        assertTrue(live2dHotSwapEligible(true, true, false, "ATRI", "Amadeus"))
    }

    @Test
    fun `no hot swap when same model or view not ready`() {
        assertFalse(live2dHotSwapEligible(true, true, false, "ATRI", "ATRI"))
        assertFalse(live2dHotSwapEligible(true, false, false, "ATRI", "Amadeus"))
        assertFalse(live2dHotSwapEligible(false, true, false, "ATRI", "Amadeus"))
    }
}
