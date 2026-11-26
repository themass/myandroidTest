package com.openapi.feature.video.prototype

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 返回键判定层：targetSdk 37 + predictive back 下系统走 OnBackPressedDispatcher，
 * 页面自己必须在回调里按同一顺序判定（锁定 → 全屏 → 关页），这里先钉死顺序。
 */
class BackPolicyTest {

    @Test
    fun `locked state only unlocks and never leaves the page`() {
        assertEquals(BackIntent.Unlock, BackPolicy.decide(locked = true, fullscreen = false))
        assertEquals(BackPolicy.decide(locked = true, fullscreen = true), BackIntent.Unlock)
    }

    @Test
    fun `fullscreen back exits fullscreen instead of closing the page`() {
        assertEquals(BackIntent.ExitFullscreen, BackPolicy.decide(locked = false, fullscreen = true))
    }

    @Test
    fun `portrait unlocked closes the page`() {
        assertEquals(BackIntent.ClosePage, BackPolicy.decide(locked = false, fullscreen = false))
    }
}
