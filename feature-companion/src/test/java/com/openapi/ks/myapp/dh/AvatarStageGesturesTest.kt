package com.openapi.ks.myapp.dh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 舞台手势仲裁（真机投诉 2026-11-23：「live2d 无法放大，我觉得是跟弹窗冲突了」）。
 *
 * 旧结构里捏合层、点按/长按层、动作托盘薄纱是三个互不知晓的 pointerInput：
 * 单指按住 500ms 途中托盘就弹出，托盘薄纱吃掉随后落下的第二根手指，缩放死掉——
 * 弹窗与缩放从结构上必然打架。新结构是一个手势判定器，一切在**抬手时刻**仲裁
 * （规则是这两个纯函数，composable 只是搬运工）：
 * - 两指出现过：tap/长按全作废，这一笔归缩放；
 * - 拖过 slop：tap/长按作废，放大状态下单指=平移舞台；
 * - 按住超过长按时限才抬手 = 长按（弹托盘）；没超过 = 轻触。
 * 长按不再在手指还按着的中途触发——慢起手捏合（先落一指、停顿、再落第二指）
 * 里第二指永远来得及取消长按。
 */
class AvatarStageGesturesTest {

    @Test
    fun `two fingers or a drag never becomes a tap`() {
        assertTrue(stageTapDecision(twoFingerSeen = false, slopBroken = false, heldLong = false))
        assertFalse(stageTapDecision(twoFingerSeen = true, slopBroken = false, heldLong = false))
        assertFalse(stageTapDecision(twoFingerSeen = false, slopBroken = true, heldLong = false))
        assertFalse(stageTapDecision(twoFingerSeen = false, slopBroken = false, heldLong = true))
    }

    @Test
    fun `long press is a still lone finger held past the timeout`() {
        assertTrue(stageLongPressDecision(twoFingerSeen = false, slopBroken = false, heldLong = true))
        // 用户真正在做捏合：哪怕两指间隔很久，抬手时两指出现过就不弹托盘——
        // 「跟弹窗冲突」的根治点。
        assertFalse(stageLongPressDecision(twoFingerSeen = true, slopBroken = false, heldLong = true))
        assertFalse(stageLongPressDecision(twoFingerSeen = false, slopBroken = true, heldLong = true))
        // 快速点按不是长按。
        assertFalse(stageLongPressDecision(twoFingerSeen = false, slopBroken = false, heldLong = false))
    }

    @Test
    fun `gesture log carries tap and long press verdicts too`() {
        val s = stageGestureLog(
            maxFingers = 1, twoFingerSeen = false,
            startScale = 1f, endScale = 1f, startOffset = 0f, endOffset = 0f,
            tap = true, longPress = false,
        )
        assertTrue(s.contains("fingers=1"))
        assertTrue(s.contains("tap=true"))
        assertTrue(s.contains("long=false"))
    }
}
