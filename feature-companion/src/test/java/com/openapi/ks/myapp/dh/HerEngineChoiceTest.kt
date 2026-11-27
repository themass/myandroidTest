package com.openapi.ks.myapp.dh

import com.openapi.ks.myapp.companion.HerSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 客户端第二刀的纯决策（persona/her 分层架构 §4「进房」her 半）：
 * 服务端 her 摘要的 renderer 决定通话舞台用哪个引擎。
 *
 * 三条钉死的规则：
 * 1. `simple` 就是**现在的默认路径**（分层立绘 / 圆头像），所以回 null = 不换舞台，
 *    生产两行 her 都是 simple，落地后线上行为逐字节不变；
 * 2. 未知 / 空 renderer 一律回 null（回落默认，不猜）——这是「不能因为拉取失败白屏」的另一半；
 * 3. 用户在引擎菜单里手动选过（callStage 非 null）时**不被服务端覆盖**。
 */
class HerEngineChoiceTest {

    @Test
    fun `simple renderer keeps the current default stage`() {
        assertNull(engineIdForRenderer("simple"))
    }

    @Test
    fun `known renderers map to their registered engine`() {
        assertEquals(DhEngineId.LIVE2D, engineIdForRenderer("live2d"))
        assertEquals(DhEngineId.VRM, engineIdForRenderer("vrm"))
    }

    @Test
    fun `renderer matching is case insensitive`() {
        assertEquals(DhEngineId.LIVE2D, engineIdForRenderer("Live2D"))
    }

    @Test
    fun `unknown or missing renderer falls back to the default path`() {
        assertNull(engineIdForRenderer("hologram"))
        assertNull(engineIdForRenderer(""))
        assertNull(engineIdForRenderer(null))
    }

    @Test
    fun `seed her_id yields the role it is bound to`() {
        // 播种规则：her_id = <renderer>-<role>-v<N>（服务端第一刀）。
        assertEquals("linche", roleKeyFromHerId("simple-linche-v1", "simple"))
        assertEquals("sudai", roleKeyFromHerId("simple-sudai-v1", "simple"))
    }

    @Test
    fun `her_id not following the seed convention maps to no role`() {
        assertNull(roleKeyFromHerId("simple-linche", "live2d"))
        assertNull(roleKeyFromHerId("linche-v1", "simple"))
        assertNull(roleKeyFromHerId("simple--v1", "simple"))
    }

    private fun her(id: String, renderer: String, status: String = "published") =
        HerSummary(id = id, name = "", tagline = "", renderer = renderer, status = status)

    @Test
    fun `the role's default her picks the engine when the user chose nothing`() {
        val hers = listOf(her("simple-linche-v1", "simple"), her("live2d-sudai-v1", "live2d"))
        assertEquals(DhEngineId.LIVE2D, resolveCallStage(StageChoice.Unset, roleId = "sudai", hers = hers))
    }

    @Test
    fun `a simple her leaves the stage as it is`() {
        val hers = listOf(her("simple-linche-v1", "simple"))
        assertNull(resolveCallStage(StageChoice.Unset, roleId = "linche", hers = hers))
    }

    @Test
    fun `a role with no her falls back to the current default`() {
        val hers = listOf(her("simple-linche-v1", "simple"))
        assertNull(resolveCallStage(StageChoice.Unset, roleId = "someone-else", hers = hers))
        assertNull(resolveCallStage(StageChoice.Unset, roleId = null, hers = hers))
        assertNull(resolveCallStage(StageChoice.Unset, roleId = "linche", hers = emptyList()))
    }

    @Test
    fun `the user's explicit engine choice is never overridden by the server`() {
        val hers = listOf(her("live2d-linche-v1", "live2d"))
        assertEquals(DhEngineId.VRM, resolveCallStage(StageChoice.Engine(DhEngineId.VRM), roleId = "linche", hers = hers))
    }

    @Test
    fun `an explicit choice of the original image is never overridden either`() {
        // 用户显式点过「原来的形象」：将来服务端给 linche 挂 live2d her，也不许换她的脸。
        val hers = listOf(her("live2d-linche-v1", "live2d"))
        assertNull(resolveCallStage(StageChoice.Original, roleId = "linche", hers = hers))
    }

    @Test
    fun `only published her packs drive the stage`() {
        val hers = listOf(her("live2d-linche-v1", "live2d", status = "draft"))
        assertNull(resolveCallStage(StageChoice.Unset, roleId = "linche", hers = hers))
    }
}
