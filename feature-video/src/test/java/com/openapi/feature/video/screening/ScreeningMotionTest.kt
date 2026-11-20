package com.openapi.feature.video.screening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 放映场动效契约（对齐设计系统的 motion token）：
 * 转场时长、缓动方向、位移量，以及系统「减弱动态效果」开启时的降级。
 *
 * 钉住的是数字与规则本身——曲线方向对不对在真机上，但时长/降级一旦漂移，
 * 整个 App 的「丝滑感」就散掉了，所以先在这里锁死。
 */
class ScreeningMotionTest {

    @Test
    fun `进场和出场走两档时长：进场中档、出场快档`() {
        val plan = MotionPlan(reduceMotion = false)
        assertEquals(410, plan.enterDurationMs())   // --duration-medium
        assertEquals(175, plan.exitDurationMs())    // --duration-fast（exit 跟 fast）
    }

    @Test
    fun `翻页位移走水平方向，前进后退共用同一个距离`() {
        val plan = MotionPlan(reduceMotion = false)
        assertEquals(24f, plan.slideDistanceDp(), 0.001f)
    }

    @Test
    fun `减弱动态时只留不透明度，位移归零、时长压到 200ms 内`() {
        val plan = MotionPlan(reduceMotion = true)
        assertEquals(0f, plan.slideDistanceDp(), 0.001f)
        assertEquals(0f, plan.pressScale(), 0.001f)
        assertTrue(plan.enterDurationMs() <= 200)
        assertTrue(plan.exitDurationMs() <= 200)
    }

    @Test
    fun `按下缩放幅度要小：能动一下但别抢内容`() {
        val plan = MotionPlan(reduceMotion = false)
        val scale = plan.pressScale()
        assertTrue(scale in 0.005f..0.05f)
    }
}
