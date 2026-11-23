package com.openapi.ks.myapp.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「点她一下」的命中判定与上报。
 *
 * 这些用例钉住三件容易在重构里悄悄坏掉的事：
 * 1. 点上方是头、下方是身体——部位名是发给 agent 的协议值，不能随手改；
 * 2. 没有素材时不吞掉点击（返回 Ignored，界面就不会播一个空反馈）；
 * 3. 只认 TAP：拖动等事件在 v1 不该被当成「点了一下」。
 */
class SimpleAvatarTapTest {

    private fun bound(): SimpleAvatarRenderer =
        SimpleAvatarRenderer().apply {
            // 立绘随便给一张：测试只关心「有没有素材」这个门槛。
            bind(AvatarSpec(portraitRes = 1))
        }

    @Test
    fun `tapping the upper part hits the head`() {
        val renderer = bound()
        val result = renderer.onTouch(AvatarTouch(x = 0.5f, y = 0.2f, kind = AvatarTouchKind.TAP))

        assertEquals(TouchResult.Handled(AvatarRegion.HEAD), result)
        assertEquals(AvatarRegion.HEAD, renderer.lastTappedRegion.value)
    }

    @Test
    fun `tapping the lower part hits the body`() {
        val renderer = bound()
        val result = renderer.onTouch(AvatarTouch(x = 0.5f, y = 0.8f, kind = AvatarTouchKind.TAP))

        assertEquals(TouchResult.Handled(AvatarRegion.BODY), result)
    }

    @Test
    fun `each tap bumps the counter so a second tap still plays the reaction`() {
        val renderer = bound()
        renderer.onTouch(AvatarTouch(0.5f, 0.2f, AvatarTouchKind.TAP))
        renderer.onTouch(AvatarTouch(0.5f, 0.2f, AvatarTouchKind.TAP))

        // 用计数而不是布尔值：连点两下要各播一次反馈。
        assertEquals(2L, renderer.tapCount.value)
    }

    @Test
    fun `a tap without any avatar bound is ignored`() {
        val renderer = SimpleAvatarRenderer()
        val result = renderer.onTouch(AvatarTouch(0.5f, 0.2f, AvatarTouchKind.TAP))

        assertEquals(TouchResult.Ignored, result)
        assertNull(renderer.lastTappedRegion.value)
        assertEquals(0L, renderer.tapCount.value)
    }

    @Test
    fun `only taps count — down and move are not a tap`() {
        val renderer = bound()
        assertEquals(TouchResult.Ignored, renderer.onTouch(AvatarTouch(0.5f, 0.2f, AvatarTouchKind.DOWN)))
        assertEquals(TouchResult.Ignored, renderer.onTouch(AvatarTouch(0.5f, 0.2f, AvatarTouchKind.MOVE)))
        assertEquals(0L, renderer.tapCount.value)
    }

    @Test
    fun `the renderer advertises tap support so the ui can rely on it`() {
        assertTrue(AvatarCapability.TOUCH_TAP in SimpleAvatarRenderer().capabilities)
    }
}
