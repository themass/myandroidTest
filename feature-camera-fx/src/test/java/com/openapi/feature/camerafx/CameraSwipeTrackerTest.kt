package com.openapi.feature.camerafx

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 滑动跟踪的正负样本（合成 21 点，只关心点 8）。
 *
 * 口径：tracker 内部把 x 镜像（用户所见即方向），位移用**宽单位**（y 乘 imgH/imgW）。
 * 测试统一 720x960（aspect=4/3），阈值取宽裕值避免浮点边界（步进 0.04/0.05）。
 * 帧 1 只立锚点，累加从帧 2 开始。
 */
class CameraSwipeTrackerTest {

    private fun handAt(x: Float, y: Float): HandFx {
        // 21 个点全部放同一个位置：tracker 只取 tipIndex=8；segments 留空（本测试不画线）。
        return HandFx(segments = emptyList(), gesture = HandGestureKind.UNKNOWN, points = List(21) { Point2d(x, y) })
    }

    @Test
    fun horizontalDragRightTriggersOnce() {
        val t = FingerSwipeTracker()
        // 原始 x 递减 = 镜像空间向右；6 帧喂入（5 个位移 × 0.04 = 0.20 ≥ 0.18）第 6 帧触发。
        var dir = SwipeDirection.NONE
        for (i in 0 until 6) {
            dir = t.update(listOf(handAt(0.3f - 0.04f * (i + 1), 0.5f)), 720, 960)
        }
        assertEquals(SwipeDirection.RIGHT, dir)
        // 同方向继续滑：默认冷却 24 帧内不再触发。
        for (i in 0 until 5) {
            assertEquals(SwipeDirection.NONE, t.update(listOf(handAt(0.02f - 0.04f * i, 0.5f)), 720, 960))
        }
    }

    @Test
    fun secondSwipeAfterCooldownTriggersAgain() {
        val t = FingerSwipeTracker(cooldownFrames = 10)
        var dir = SwipeDirection.NONE
        repeat(6) { dir = t.update(listOf(handAt(0.3f - 0.04f * (it + 1), 0.5f)), 720, 960) }
        assertEquals(SwipeDirection.RIGHT, dir) // 帧 6 触发 → 冷却到帧 16
        // 冷却内立刻再横滑一次：全程被压掉。
        repeat(4) { assertEquals(SwipeDirection.NONE, t.update(listOf(handAt(0.6f - 0.05f * (it + 1), 0.5f)), 720, 960)) }
        // 帧 11-16 停手（锚点原地不动，位移 0）。
        repeat(6) { assertEquals(SwipeDirection.NONE, t.update(listOf(handAt(0.4f, 0.5f)), 720, 960)) }
        // 帧 17-20 再滑（4 × 0.05 = 0.20）→ 出冷却后触发。
        dir = SwipeDirection.NONE
        repeat(4) { dir = t.update(listOf(handAt(0.4f - 0.05f * (it + 1), 0.5f)), 720, 960) }
        assertEquals(SwipeDirection.RIGHT, dir)
    }

    @Test
    fun inPlaceJitterNeverTriggers() {
        // 负样本：原地晃手（A→B→A→B…）。两点法会因采样相位误报，带符号累加器会抵消。
        val t = FingerSwipeTracker()
        for (i in 0 until 60) {
            val x = if (i % 2 == 0) 0.3f else 0.35f
            assertEquals("第 $i 帧不该触发", SwipeDirection.NONE, t.update(listOf(handAt(x, 0.5f)), 720, 960))
        }
    }

    @Test
    fun tinyMoveBelowThresholdNeverTriggers() {
        val t = FingerSwipeTracker()
        var dir = SwipeDirection.NONE
        // 总位移 4 × 0.03 = 0.12 < 0.18：不触发。
        for (i in 0 until 5) {
            dir = t.update(listOf(handAt(0.3f - 0.03f * (i + 1), 0.5f)), 720, 960)
        }
        assertEquals(SwipeDirection.NONE, dir)
    }

    @Test
    fun subJitterStepsIgnored() {
        val t = FingerSwipeTracker()
        var dir = SwipeDirection.NONE
        // 每帧 0.005 < jitterEpsilon(0.006)：全部当噪声丢弃，哪怕 40 帧攒出 0.2 总位移。
        for (i in 0 until 40) {
            dir = t.update(listOf(handAt(0.3f - 0.005f * (i + 1), 0.5f)), 720, 960)
        }
        assertEquals(SwipeDirection.NONE, dir)
    }

    @Test
    fun slowDragTimesOutAndRestarts() {
        val t = FingerSwipeTracker(maxStrokeFrames = 10)
        var dir = SwipeDirection.NONE
        // 每轮挪 0.05 后停 5 帧：起笔窗口被超时打断，小步慢移永不触发。
        for (round in 0 until 4) {
            val x = 0.4f - 0.05f * (round + 1)
            repeat(6) { dir = t.update(listOf(handAt(x, 0.5f)), 720, 960) }
        }
        assertEquals(SwipeDirection.NONE, dir)
    }

    @Test
    fun verticalDragUpUsesAspectUnits() {
        val t = FingerSwipeTracker()
        // y 步进 0.05/帧；宽单位 = 0.05 × (960/720) ≈ 0.0667 → 第 4 帧累加 ≈ 0.2 ≥ 0.18。
        var dir = SwipeDirection.NONE
        for (i in 0 until 4) {
            dir = t.update(listOf(handAt(0.5f, 0.6f - 0.05f * (i + 1))), 720, 960)
        }
        assertEquals(SwipeDirection.UP, dir)
    }

    @Test
    fun diagonalMoveIsIgnored() {
        val t = FingerSwipeTracker()
        var dir = SwipeDirection.NONE
        // x、y 每帧宽单位都是 0.04（y 0.03×4/3）：主轴不超过副轴 × 1.6 → 不算滑动。
        for (i in 0 until 8) {
            dir = t.update(listOf(handAt(0.3f - 0.04f * (i + 1), 0.5f - 0.03f * (i + 1))), 720, 960)
        }
        assertEquals(SwipeDirection.NONE, dir)
    }

    @Test
    fun handLossBeyondGraceReanchorsWithoutJump() {
        val t = FingerSwipeTracker(graceFrames = 2)
        t.update(listOf(handAt(0.3f, 0.5f)), 720, 960)
        // 手消失 10 帧（> grace）→ 锚点作废；重新出现在远处只立新锚，不产生假位移。
        var dir = SwipeDirection.NONE
        repeat(10) { dir = t.update(emptyList(), 720, 960) }
        assertEquals(SwipeDirection.NONE, dir)
        dir = t.update(listOf(handAt(0.9f, 0.1f)), 720, 960)
        assertEquals(SwipeDirection.NONE, dir)
    }

    @Test
    fun multiHandKeepsTrackingClosestToOne() {
        val t = FingerSwipeTracker()
        // 先跟踪 0.3 处的手。
        t.update(listOf(handAt(0.3f, 0.5f)), 720, 960)
        // 再来一只手在 0.05：tracker 续接离锚点近的那只（0.3→0.27…），而不是跳到 0.05
        // （跳到 0.05 会瞬间攒出 ~0.25 的假位移直接触发 LEFT）。
        var dir = SwipeDirection.NONE
        for (i in 0 until 3) {
            dir = t.update(
                listOf(handAt(0.05f, 0.5f), handAt(0.3f - 0.03f * (i + 1), 0.5f)),
                720, 960,
            )
        }
        assertEquals(SwipeDirection.NONE, dir)
    }

    @Test
    fun resetClearsCooldownAndAnchor() {
        val t = FingerSwipeTracker(cooldownFrames = 50)
        var dir = SwipeDirection.NONE
        repeat(6) { dir = t.update(listOf(handAt(0.3f - 0.04f * (it + 1), 0.5f)), 720, 960) }
        assertEquals(SwipeDirection.RIGHT, dir)
        t.reset()
        dir = SwipeDirection.NONE
        repeat(6) { dir = t.update(listOf(handAt(0.3f - 0.04f * (it + 1), 0.5f)), 720, 960) }
        assertEquals(SwipeDirection.RIGHT, dir)
    }

    @Test
    fun cueMapsOnlyHorizontalToVocabulary() {
        assertEquals("swipe", CameraSwipeCue.stageGestureId(SwipeDirection.LEFT))
        assertEquals("swipe", CameraSwipeCue.stageGestureId(SwipeDirection.RIGHT))
        assertEquals(null, CameraSwipeCue.stageGestureId(SwipeDirection.UP))
        assertEquals(null, CameraSwipeCue.stageGestureId(SwipeDirection.DOWN))
        assertEquals(null, CameraSwipeCue.stageGestureId(SwipeDirection.NONE))
    }
}
