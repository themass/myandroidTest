package com.openapi.feature.video.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 倍速单击循环的判定层：chip 单击走 1→1.25→1.5→2→3→0.75→1，
 * 长按开面板精确选的档位集合统一为 {0.5,0.75,1,1.25,1.5,2,2.5,3}。
 */
class SpeedStepsTest {

    @Test
    fun `single tap cycle follows the designed order`() {
        var speed = 1f
        val expected = listOf(1.25f, 1.5f, 2f, 3f, 0.75f, 1f)
        for (next in expected) {
            speed = SpeedSteps.nextAfter(speed)
            assertEquals(next, speed, 0.0001f)
        }
    }

    @Test
    fun `cycle from every panel step returns to 1 within one loop`() {
        for (start in SpeedSteps.panelSteps) {
            var speed = start
            do {
                speed = SpeedSteps.nextAfter(speed)
            } while (speed != start && speed != 1f)
            assertEquals(1f, speed, 0.0001f)
        }
    }

    @Test
    fun `unknown speed falls into the cycle at 1`() {
        assertEquals(1f, SpeedSteps.nextAfter(4f), 0.0001f)
        assertEquals(1f, SpeedSteps.nextAfter(1.1f), 0.0001f)
    }

    @Test
    fun `panel steps are the unified seven plus 2_5 set ascending`() {
        assertEquals(
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f),
            SpeedSteps.panelSteps.toList(),
        )
    }

    @Test
    fun `cycle steps are a subset of panel steps`() {
        for (s in listOf(1f, 1.25f, 1.5f, 2f, 3f, 0.75f)) {
            assertTrue("cycle step $s missing from panel", SpeedSteps.panelSteps.any { it == s })
        }
    }
}
