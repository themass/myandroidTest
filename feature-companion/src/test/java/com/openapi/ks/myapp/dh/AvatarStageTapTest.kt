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

    // ---- demo #2b：Live2D 全身立绘的细分区（头/手/躯干/脚） ----
    // 阈值来自真机截图实测（demo #2b 笔记）：ATRI 占位 norm_y≈0.10–0.73；
    // 手臂在 0.42–0.57 一带向两侧展开（x 最宽 0.33–0.71），0.57 以下收窄成双腿，
    // 两条不同服装（校服/比基尼）的截图实测一致——分区与服装无关。

    @Test
    fun `live2d 细分区：四点一带是头，双腿一带是脚`() {
        assertEquals("head", live2dTapZone(0.5f, 0.10f))
        assertEquals("head", live2dTapZone(0.5f, 0.41f))
        // 脚：腿带从 0.57 起（中缝 x≈0.5 也是腿，不再当躯干）
        assertEquals("foot", live2dTapZone(0.5f, 0.62f))
        assertEquals("foot", live2dTapZone(0.51f, 0.71f))
        assertEquals("foot", live2dTapZone(0.5f, 0.99f))
    }

    @Test
    fun `live2d 细分区：臂展两翼是手，中列是躯干`() {
        // 手：躯干带（0.42–0.57）里 x 离中轴 ≥0.12 —— 实测手臂展开到 x 0.33/0.71
        assertEquals("hand", live2dTapZone(0.33f, 0.50f))
        assertEquals("hand", live2dTapZone(0.68f, 0.46f))
        assertEquals("hand", live2dTapZone(0.36f, 0.44f))
        // 躯干：同一 y 带里的中列——demo #2 的换装语义留在身上
        assertEquals("body", live2dTapZone(0.5f, 0.45f))
        assertEquals("body", live2dTapZone(0.44f, 0.55f))
    }

    @Test
    fun `live2d 细分区：越界坐标同样收敛，不产生未知区域`() {
        assertEquals("head", live2dTapZone(-3f, 0.2f))
        assertEquals("foot", live2dTapZone(2f, 5f))
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
