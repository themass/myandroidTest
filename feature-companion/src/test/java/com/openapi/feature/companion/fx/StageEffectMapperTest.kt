package com.openapi.feature.companion.fx

import com.openapi.ks.myapp.companion.TransportConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** StageEffectMapper 纯函数钉桩：未知输入 null，绝不抛。 */
class StageEffectMapperTest {

    @Test
    fun fromGesture_knownIds() {
        val heart = StageEffectMapper.fromGesture("pat_head") as StageEffect.Play
        assertEquals(FxAssets.PAT_HEAD, heart.assetId)
        assertEquals(StageEffect.Anchor.HEAD, heart.anchor)

        val wave = StageEffectMapper.fromGesture("WAVE") as StageEffect.Play
        assertEquals(FxAssets.WAVE, wave.assetId)
        assertEquals(StageEffect.Anchor.HAND, wave.anchor)

        val petals = StageEffectMapper.fromGesture(StageEffectMapper.GESTURE_CONNECTED) as StageEffect.Play
        assertEquals(FxAssets.CONNECTED, petals.assetId)
        assertEquals(StageEffect.Anchor.FULL, petals.anchor)

        // 手指滑动（本地 cue，见 camera-fx CameraSwipeCue）→ 流光横扫
        val streak = StageEffectMapper.fromGesture("swipe") as StageEffect.Play
        assertEquals(FxAssets.SWIPE, streak.assetId)
        assertEquals(StageEffect.Anchor.FULL, streak.anchor)
    }

    @Test
    fun fromGesture_aliasesAndUnknown() {
        assertEquals(FxAssets.PAT_HEAD, (StageEffectMapper.fromGesture("head_pat") as StageEffect.Play).assetId)
        assertNull(StageEffectMapper.fromGesture(null))
        assertNull(StageEffectMapper.fromGesture(""))
        assertNull(StageEffectMapper.fromGesture("spin_twice"))
    }

    @Test
    fun fromAction_trayIds() {
        assertEquals(FxAssets.PAT_HEAD, (StageEffectMapper.fromAction("pat_head") as StageEffect.Play).assetId)
        assertEquals(FxAssets.WAVE, (StageEffectMapper.fromAction("wave") as StageEffect.Play).assetId)
        assertNull(StageEffectMapper.fromAction("sit_close"))
        assertNull(StageEffectMapper.fromAction("turn"))
        assertNull(StageEffectMapper.fromAction(null))
    }

    @Test
    fun everyRegisteredAssetExistsOnDisk() {
        // 白名单与资产文件必须同步：词表加了 id 但忘放 JSON，运行时永远「加载…」，
        // 单测先把这层钉住（src/main/assets 从测试工作目录直达）。
        val assetsDir = java.io.File("src/main/assets/fx")
        assertTrue("找不到 $assetsDir.absolutePath", assetsDir.isDirectory)
        for (spec in FxAssets.all) {
            val f = java.io.File(assetsDir, "${spec.assetId}.json")
            assertTrue("缺资产：${spec.assetId}", f.isFile)
            assertTrue("${spec.assetId} 超过 100KB", f.length() <= 100_000L)
        }
    }

    @Test
    fun onCallState_firstConnectOnly() {
        val first = StageEffectMapper.onCallState(TransportConnection.CONNECTING, TransportConnection.CONNECTED)
        assertTrue(first is StageEffect.Play)
        assertEquals(FxAssets.CONNECTED, (first as StageEffect.Play).assetId)

        // 冷启动 null→CONNECTED 也算首接通
        assertTrue(StageEffectMapper.onCallState(null, TransportConnection.CONNECTED) is StageEffect.Play)

        // 重连恢复不播
        assertNull(StageEffectMapper.onCallState(TransportConnection.RECONNECTING, TransportConnection.CONNECTED))
        assertNull(StageEffectMapper.onCallState(TransportConnection.DISCONNECTED, TransportConnection.CONNECTED))
        assertNull(StageEffectMapper.onCallState(TransportConnection.CONNECTED, TransportConnection.CONNECTED))

        // 非接通边不播
        assertNull(StageEffectMapper.onCallState(null, TransportConnection.CONNECTING))
        assertNull(StageEffectMapper.onCallState(TransportConnection.CONNECTED, TransportConnection.DISCONNECTED))
    }
}
