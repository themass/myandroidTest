package com.openapi.ks.myapp.dh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Live2dHeadSwayTest {
    @Test
    fun `closed mouth returns to center`() {
        assertEquals(0f to 0f, Live2dHeadSway.gaze(0f, 12345L))
    }

    @Test
    fun `speaking sways within bounds and actually moves`() {
        val samples = (0 until 200).map { Live2dHeadSway.gaze(0.8f, it * 50L) }
        assertTrue(samples.all { (x, y) -> x in -1f..1f && y in -1f..1f })
        assertTrue(samples.maxOf { it.first } - samples.minOf { it.first } > 0.3f)
        assertTrue(samples.maxOf { it.second } - samples.minOf { it.second } > 0.1f)
    }
}
