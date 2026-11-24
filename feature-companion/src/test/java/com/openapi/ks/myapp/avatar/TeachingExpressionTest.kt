package com.openapi.ks.myapp.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 表情映射的钉桩测试（纯决策层）：只用已有姿态字段，不新增美术。
 * 同时钉住「IDLE = 零增量」——通话页默认路径必须与加这个概念之前完全一致。
 */
class TeachingExpressionTest {

    @Test
    fun `IDLE 不产生任何姿态增量`() {
        val t = teacherPoseTargets(TeacherExpression.IDLE)
        assertEquals(0f, t.tiltDeg)
        assertEquals(0f, t.blush)
        assertEquals(0f, t.handLiftPx)
        assertTrue(!t.eyesHalf)
    }

    @Test
    fun `表扬抬右手且脸红眯眼`() {
        val t = teacherPoseTargets(TeacherExpression.PRAISE)
        assertTrue("抬手向上为负", t.handLiftPx < 0f)
        assertTrue("轻脸红", t.blush in 0f..1f && t.blush > 0f)
        assertTrue(t.eyesHalf)
    }

    @Test
    fun `倾听与沉吟倾斜方向相反`() {
        assertTrue(teacherPoseTargets(TeacherExpression.LISTEN).tiltDeg > 0f)
        assertTrue(teacherPoseTargets(TeacherExpression.THINK).tiltDeg < 0f)
    }

    @Test
    fun `表情幅度都在克制范围内`() {
        TeacherExpression.entries.forEach { e ->
            val t = teacherPoseTargets(e)
            assertTrue("$e 倾头幅度应 <20°", kotlin.math.abs(t.tiltDeg) < 20f)
            assertTrue("$e 脸红必须 0..1", t.blush in 0f..1f)
            assertTrue("$e 抬手上限 40px", kotlin.math.abs(t.handLiftPx) <= 40f)
        }
    }

    @Test
    fun `引擎收敛向目标且不破坏眨眼`() {
        val engine = LayeredAvatarEngine(kotlin.random.Random(42))
        // IDLE：长时间跑，rootRot 只在待机幅度内。
        repeat(120) { engine.update(1 / 60f) }
        val idle = engine.update(1 / 60f)
        assertTrue("IDLE 姿态不应有明显倾斜", kotlin.math.abs(idle.rootRot) < 5f)
        assertEquals(0f, idle.handRY, 0.5f)

        // 切 PRAISE：收敛若干帧后手应抬起、脸红出现。
        engine.expression = TeacherExpression.PRAISE
        repeat(60) { engine.update(1 / 60f) }
        val praised = engine.update(1 / 60f)
        assertTrue("表扬应抬手", praised.handRY < -5f)
        assertTrue("表扬应有脸红", praised.blush > 0.1f)

        // 回到 IDLE：应再次收敛回零。
        engine.expression = TeacherExpression.IDLE
        repeat(120) { engine.update(1 / 60f) }
        val back = engine.update(1 / 60f)
        assertEquals(0f, back.handRY, 1f)
        assertTrue(back.blush < 0.05f)
    }
}
