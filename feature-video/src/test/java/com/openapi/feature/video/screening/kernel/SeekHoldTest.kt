package com.openapi.feature.video.screening.kernel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `seek 缓冲期间内核报小值（精确 seek 假进度）同样钉住`() {
        // 真机：IJK accurate seek 期间 currentPosition 报几百毫秒——缓冲期位置钉住
        assertEquals(
            2_475_773L,
            playerPositionOrHold(held = 2_475_773L, playerPosition = 342L, playing = true, buffering = true),
        )
    }

    @Test
    fun `向后拖缓冲期间内核报拖前高位，不能弹回旧位置`() {
        // 真机 23:04 复验：从 68 分拖回 20 分，缓冲期内核报的还是拖前的 68 分高位
        assertEquals(
            1_200_000L,
            playerPositionOrHold(held = 1_200_000L, playerPosition = 4_087_836L, playing = true, buffering = true),
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

    // —— 落点判定（awaitingSeekAfter）：全部用例来自探针真机日志（2026-11-22） ——

    private val target = 4_087_836L // 真机：拖到 68:07.836

    @Test
    fun `假读数=目标值本身（IJK 缓存期）不能算落点`() {
        // 探针实测：seekTo 后头三拍 p==目标值本身——信任窗内一律不落点
        assertTrue(
            awaitingSeekAfter(awaiting = true, playing = true, sinceIssuedMs = 400L, playerPosition = target, target = target),
        )
    }

    @Test
    fun `信任窗内报 0 继续等`() {
        assertTrue(
            awaitingSeekAfter(awaiting = true, playing = true, sinceIssuedMs = 1_000L, playerPosition = 0L, target = target),
        )
    }

    @Test
    fun `过信任窗后真落点解除等待`() {
        assertFalse(
            awaitingSeekAfter(awaiting = true, playing = true, sinceIssuedMs = 6_000L, playerPosition = target + 40L, target = target),
        )
    }

    @Test
    fun `向后拖时拖前旧读数不算落点（上界挡住弹回）`() {
        // 从 68 分拖到 40 分：seek 刚发出时读数还是 68 分——过窗后也不许命中，否则进度条弹回旧位置
        assertFalse(
            awaitingSeekAfter(awaiting = false, playing = true, sinceIssuedMs = 0L, playerPosition = target, target = target),
        )
        assertTrue(
            awaitingSeekAfter(awaiting = true, playing = true, sinceIssuedMs = 4_000L, playerPosition = 2_460_000L, target = target),
        )
    }

    @Test
    fun `落点永不追上有兜底：30 秒后放回跟内核（不永远钉死）`() {
        assertFalse(
            awaitingSeekAfter(awaiting = true, playing = true, sinceIssuedMs = 31_000L, playerPosition = 0L, target = target),
        )
    }

    @Test
    fun `暂停时不做落点判定`() {
        assertTrue(
            awaitingSeekAfter(awaiting = true, playing = false, sinceIssuedMs = 6_000L, playerPosition = target, target = target),
        )
    }
}
