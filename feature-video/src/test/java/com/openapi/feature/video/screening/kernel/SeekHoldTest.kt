package com.openapi.feature.video.screening.kernel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * seek 后缓冲期间的位置同步（[playerPositionOrHold]）钉住「拖动进度条不弹回 0」。
 * 真机回归（2026-11-22 用户点名）：拖到 41 分处，IJK 精确 seek 缓冲十几秒，
 * 期间内核 currentPosition 报 0，UI 一路显示 0:00——用户以为拖回 0 了，实际几秒后又跳回 41:41。
 */
class SeekHoldTest {

    @Test
    fun `seek 缓冲期间内核报 0，位置必须钉在用户拖到的目标`() {
        // held=用户刚 seek 到的 41:15；playing=true 还在播，buffering=true 精确 seek 缓冲中
        assertEquals(
            2_475_773L,
            playerPositionOrHold(held = 2_475_773L, playerPosition = 0L, playing = true, buffering = true),
        )
    }

    @Test
    fun `缓冲结束照常跟随内核位置`() {
        assertEquals(
            2_478_900L,
            playerPositionOrHold(held = 2_475_773L, playerPosition = 2_478_900L, playing = true, buffering = false),
        )
    }

    @Test
    fun `暂停时不覆盖（原语义保留）`() {
        assertEquals(
            2_475_773L,
            playerPositionOrHold(held = 2_475_773L, playerPosition = 5_000L, playing = false, buffering = false),
        )
    }
}
