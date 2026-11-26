package com.openapi.chatkit.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A43-12 语音输入弹跳的「音量 → 弹跳量」曲线（协调会话要求：纯决策函数先写单测；
 * 最终观感留真机复验）。
 *
 * 用户投诉按住说话时浮层几乎不动：旧公式 `breath + 0.2f * level` 在正常说话的音量档
 * （level≈0.2）只贡献 **0.04** 的缩放——肉眼完全看不出来。曲线必须同时满足：
 * - 完全安静不弹（0 → 0，浮层只剩呼吸），别让静音时画面自己跳；
 * - 正常说话就明显弹（0.2 档至少 0.1：旧公式只有 0.04，这里是它的 2.5 倍以上）；
 * - 大嗓门不失控（封顶 0.28——340dp 的浮层放超过 1.3 倍会糊出屏幕），全程单调不减；
 * - 输入先钳位到 [0,1]（历史采样可能越界）。
 *
 * 膝盖点（0.35）以下是大声场增益段：手机贴嘴说话很容易到 0.6+，若线性到底要么
 * 小声看不见、要么大喊糊屏——分段就是解这个两难。
 */
class RippleBounceTest {

    @Test
    fun silenceBouncesNothing() {
        assertEquals(0f, rippleBounce(0f), 1e-6f)
    }

    @Test
    fun normalSpeechVolumeVisiblyBounces() {
        // 旧公式在 0.2 档只给 0.04（投诉的根源）；新曲线必须至少 0.1
        assertTrue("0.2 档至少 0.1，实际 ${rippleBounce(0.2f)}", rippleBounce(0.2f) >= 0.1f)
    }

    @Test
    fun kneeAndCeilingAreExact() {
        // 膝盖点：0.35 → 0.2；顶格：1.0 → 0.28（旧公式顶格只有 0.2，且中间全程偏软）
        assertEquals(0.2f, rippleBounce(0.35f), 1e-5f)
        assertEquals(0.28f, rippleBounce(1f), 1e-5f)
    }

    @Test
    fun curveIsMonotonicAndCapped() {
        var previous = -1f
        var sample = 0f
        while (sample <= 1.0001f) {
            val value = rippleBounce(sample)
            assertTrue("音量 $sample 处弹跳量回退了：$value < $previous", value >= previous)
            assertTrue("音量 $sample 处越出上限：$value", value <= 0.28f + 1e-5f)
            previous = value
            sample += 0.05f
        }
    }

    @Test
    fun outOfRangeLevelsAreClamped() {
        assertEquals(0f, rippleBounce(-1f), 1e-6f)
        assertEquals(0.28f, rippleBounce(3f), 1e-5f)
    }
}
