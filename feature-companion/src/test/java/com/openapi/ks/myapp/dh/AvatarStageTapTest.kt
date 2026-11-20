package com.openapi.ks.myapp.dh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 数字人舞台「点她一下」的纯函数契约。
 *
 * 反馈必须先在本地成立（缩放脉冲 + 上报区域给 agent），不等网络——点了没反应
 * 是体验底线；agent 回不回、怎么回是它的判断，客户端不替她说话。
 */
class AvatarStageTapTest {

    @Test
    fun `上半身判头 下半身判身体，与旧版分层立绘的区域名一致`() {
        assertEquals("head", stageTapRegion(0.5f, 0.10f))
        assertEquals("head", stageTapRegion(0.5f, 0.40f))
        assertEquals("body", stageTapRegion(0.5f, 0.45f))
        assertEquals("body", stageTapRegion(0.5f, 0.99f))
    }

    @Test
    fun `越界坐标收敛回零点一到一，不产生第三种区域`() {
        assertTrue(stageTapRegion(-3f, 0.2f) == "head")
        assertTrue(stageTapRegion(2f, 5f) == "body")
    }

    @Test
    fun `按下脉冲：缩 2 个百分点，回弹比按下慢`() {
        val p = stageTapPulse(reducedMotion = false)
        assertTrue("缩放量要小而可见", p.scaleDelta in 0.01f..0.03f)
        assertTrue("按下快", p.pressDownMs in 60..160)
        assertTrue("回弹慢一点，像弹性", p.returnMs >= p.pressDownMs && p.returnMs <= 260)
    }

    @Test
    fun `减弱动态时不缩放，反馈靠震动，时长压短`() {
        val p = stageTapPulse(reducedMotion = true)
        assertEquals(0f, p.scaleDelta, 0.0001f)
        assertTrue(p.pressDownMs <= 200)
        assertTrue(p.returnMs <= 200)
    }
}
