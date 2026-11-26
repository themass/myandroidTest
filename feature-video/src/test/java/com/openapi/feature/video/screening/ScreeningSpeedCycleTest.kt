package com.openapi.feature.video.screening

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A40 倍速单击循环（用户 2026-11-26：「倍速要一下一下点，不用每次钻进面板」）。
 *
 * 现在点胶囊是「胶囊→面板」两跳。改法：单击沿档位表走一格
 * （1→1.25→1.5→2→3→0.75→1），长按仍开现成面板（面板里还有 .5/2.5 两档，
 * 用户点名过的，不动）。档位顺序收成 screening/ 里一份共享常量。
 * 胶囊文案本来就显示当前倍速（speed==1 显示「倍速」），切完一眼可见。
 */
class ScreeningSpeedCycleTest {

    @Test
    fun `单击按 1→1点25→1点5→2→3→0点75→1 循环`() {
        assertEquals(1.25f, nextSpeedGear(1f), 0.001f)
        assertEquals(1.5f, nextSpeedGear(1.25f), 0.001f)
        assertEquals(2f, nextSpeedGear(1.5f), 0.001f)
        assertEquals(3f, nextSpeedGear(2f), 0.001f)
        assertEquals(0.75f, nextSpeedGear(3f), 0.001f)
        assertEquals(1f, nextSpeedGear(0.75f), 0.001f)
    }

    @Test
    fun `表外档位（面板里的 0点5 与 2点5）单击回落到 1 倍`() {
        // 这两档只有面板能选：单击落在它们上时继续「往前」语义说不清，
        // 回 1 倍是最可预测的落点。
        assertEquals(1f, nextSpeedGear(0.5f), 0.001f)
        assertEquals(1f, nextSpeedGear(2.5f), 0.001f)
    }

    @Test
    fun `单击走表、长按开面板——两条胶囊都不许再是纯 clickable 直接开面板`() {
        val src = File("src/main/java/com/openapi/feature/video/screening/ScreeningPlayback.kt").readText()
        // iconOnly 走带与信息态胶囊各一处 combinedClickable（单击=切档，长按=面板）。
        assertEquals("两处胶囊都要 combinedClickable", 2, Regex("""combinedClickable\(""").findAll(src).count())
        // 单击切档要接到内核 seams：speed 与 host.setSpeed 一起走。
        assertTrue("单击要同时更新 speed 与 host.setSpeed",
            Regex("""host\.setSpeed\(""").findAll(src).count() >= 2)
        // 档位表只有一份。
        assertEquals("SPEED_CYCLE 只能定义一次", 1, Regex("""val SPEED_CYCLE""").findAll(src).count())
    }
}
