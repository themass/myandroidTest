package com.openapi.ks.myapp.dh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 引擎下拉切换的决策。曾经的 bug：拿「最初引擎 id」比较，被熔断降级成 SIMPLE 的用户
 * 先点 Live2D 再点回最初的 Duix，会被当成「没变」吞掉——菜单打勾 Duix，屏幕还是 Live2D，
 * 表现为「唇形一直闭口」。
 */
class DhEngineSwapPolicyTest {

    @Test
    fun first_composition_with_same_id_does_not_swap() {
        assertNull(DhEngineSwapPolicy.target(DhEngineId.DUIX, lastRequested = DhEngineId.DUIX) { false })
    }

    @Test
    fun reselecting_the_initial_engine_after_another_one_swaps_back() {
        // 初始 DUIX → 用户选 LIVE2D（last=LIVE2D）→ 再选回 DUIX
        assertEquals(
            DhEngineId.DUIX,
            DhEngineSwapPolicy.target(DhEngineId.DUIX, lastRequested = DhEngineId.LIVE2D) { false },
        )
    }

    @Test
    fun quarantined_engine_degrades_to_simple() {
        assertEquals(
            DhEngineId.SIMPLE,
            DhEngineSwapPolicy.target(DhEngineId.TAO_AVATAR, lastRequested = DhEngineId.SIMPLE) { it == DhEngineId.TAO_AVATAR },
        )
    }
}
