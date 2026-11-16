package com.openapi.ks.myapp.avatar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 嘴型由音频振幅驱动。
 *
 * 以前 `AvatarCommand.Mouth` 的整条链路（阈值判定、SPEAKING 门槛）都写好了，但全仓库
 * 没有一处调用它——两帧嘴型画好了却永远不动。这些用例钉住那条链路的行为，
 * 以及一个容易踩的坑：不在 SPEAKING 时，残留的振幅不该让嘴动起来。
 */
class SimpleAvatarMouthTest {

    private fun speaking(): SimpleAvatarRenderer =
        SimpleAvatarRenderer().apply {
            bind(AvatarSpec(portraitRes = 1))
            dispatch(AvatarCommand.SetState(AvatarState.SPEAKING))
        }

    @Test
    fun `loud audio opens the mouth`() {
        val renderer = speaking()
        renderer.dispatch(AvatarCommand.Mouth(openness = 0.9f))
        assertTrue("0.9 远超阈值，应该张嘴", renderer.mouthOpen.value)
    }

    @Test
    fun `quiet audio keeps it shut`() {
        val renderer = speaking()
        renderer.dispatch(AvatarCommand.Mouth(openness = 0.1f))
        assertFalse("0.1 在阈值以下，应该闭嘴", renderer.mouthOpen.value)
    }

    @Test
    fun `the threshold sits between quiet and loud`() {
        val renderer = speaking()
        // 0.35 是阈值：刚好在下面闭嘴、在上面张嘴。这个数字要和 VoiceRecorder 的
        // 增益口径（×4）一起看，单改一边会让真实语音永远张不开嘴。
        renderer.dispatch(AvatarCommand.Mouth(openness = 0.34f))
        assertFalse(renderer.mouthOpen.value)
        renderer.dispatch(AvatarCommand.Mouth(openness = 0.36f))
        assertTrue(renderer.mouthOpen.value)
    }

    @Test
    fun `amplitude is ignored when she is not speaking`() {
        val renderer = SimpleAvatarRenderer().apply {
            bind(AvatarSpec(portraitRes = 1))
            dispatch(AvatarCommand.SetState(AvatarState.IDLE))
        }
        renderer.dispatch(AvatarCommand.Mouth(openness = 1f))
        // 她没在说话时，麦克风那边的残留振幅不该让她的嘴动。
        assertFalse(renderer.mouthOpen.value)
    }

    @Test
    fun `leaving the speaking state closes the mouth`() {
        val renderer = speaking()
        renderer.dispatch(AvatarCommand.Mouth(openness = 1f))
        assertTrue(renderer.mouthOpen.value)

        renderer.dispatch(AvatarCommand.SetState(AvatarState.IDLE))
        // 上一段音频的嘴型不能卡在张开状态。
        assertFalse(renderer.mouthOpen.value)
    }

    @Test
    fun `non-finite levels are dropped instead of poisoning the comparison`() {
        val renderer = speaking()
        renderer.dispatch(AvatarCommand.Mouth(openness = Float.NaN))
        assertFalse("NaN 与阈值比较永远为 false 之外的行为不可依赖，直接丢", renderer.mouthOpen.value)

        renderer.dispatch(AvatarCommand.Mouth(openness = Float.POSITIVE_INFINITY))
        assertFalse(renderer.mouthOpen.value)
    }
}
