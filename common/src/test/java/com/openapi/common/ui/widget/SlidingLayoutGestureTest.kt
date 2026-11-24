package com.openapi.common.ui.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B6'：左缘滑动返回判定的纯函数测试（累计位移 + touch slop）。
 *
 * 原实现只看相邻两个 MOVE 的单增量：|dx|>|dy| 对一个 1~2px 的手指抖动就成立，
 * 上下滑列表时轻点的第一个 MOVE 就能误触发返回；且拦截区（10% 屏宽）与
 * 消费区（写死 250px）口径不一。新判定：
 * 按下点在左缘区（24dp×density）内、累计 |dx| > touch slop（≈8dp×density）、
 * 且累计 |dx| > 累计 |dy|×2。
 */
class SlidingLayoutGestureTest {

    // 3x 密度机的口径：拦截区 24dp=72px，slop 8dp=24px
    private val zone = 72
    private val slop = 24f

    @Test
    fun `tap jitter inside edge zone does not trigger`() {
        // 轻点后手指抖了 2~3px——旧实现单增量 |dx|>|dy| 会误判，新判定被 slop 拦住
        assertFalse(SlidingLayout.isEdgeSwipeGesture(10, zone, 2, 1, slop))
        assertFalse(SlidingLayout.isEdgeSwipeGesture(10, zone, 3, 0, slop))
        assertFalse(SlidingLayout.isEdgeSwipeGesture(10, zone, slop.toInt(), 0, slop))
    }

    @Test
    fun `clear right swipe from edge triggers`() {
        assertTrue(SlidingLayout.isEdgeSwipeGesture(10, zone, 120, 5, slop))
        // 累计跨过 slop 即成立（哪怕分几步 MOVE 累加到 25px）
        assertTrue(SlidingLayout.isEdgeSwipeGesture(10, zone, 25, 0, slop))
    }

    @Test
    fun `touch down outside edge zone never triggers`() {
        assertFalse(SlidingLayout.isEdgeSwipeGesture(zone, zone, 300, 0, slop))
        assertFalse(SlidingLayout.isEdgeSwipeGesture(400, zone, 300, 0, slop))
        // 区内边界值：downX == zone-1 仍在区内
        assertTrue(SlidingLayout.isEdgeSwipeGesture(zone - 1, zone, 120, 0, slop))
    }

    @Test
    fun `vertical scroll is not a swipe back`() {
        // 列表上下滑：横向累计没过 slop，或纵向为主（|dx| ≤ |dy|×2）都不触发
        assertFalse(SlidingLayout.isEdgeSwipeGesture(10, zone, 100, 300, slop))
        assertFalse(SlidingLayout.isEdgeSwipeGesture(10, zone, 40, 20, slop)) // 40 == 20×2 恰好不触发
        assertTrue(SlidingLayout.isEdgeSwipeGesture(10, zone, 41, 20, slop))
    }

    @Test
    fun `zone and threshold are independent of view width`() {
        // 原实现拦截区 = getWidth()/10（平板上 ~190px、手机上 ~108px），
        // 新判定只看传入的固定区（24dp×density），不再随屏宽漂移
        assertFalse(SlidingLayout.isEdgeSwipeGesture(150, zone, 120, 0, slop))
    }
}
