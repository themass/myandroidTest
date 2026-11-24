package com.openapi.chatkit.input

import com.openapi.chatkit.model.VoiceStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 按住说话的松手去向是纯几何：三种样式各自的取消/转文字/发送阈值。
 * 这一段以前没有任何测试——手势在真机上跑，回归（比如 Floating 把右滑转文字
 * 和左滑取消的符号写反）只能靠人肉点出来。钉在这里，改阈值必须过测试。
 */
class VoiceIntentTest {

    private val t = 240f // 80dp 在 xxhdpi 的像素值，只要一致就行

    @Test
    fun `ripple swipes up past the threshold to cancel, anything else sends`() {
        assertEquals(VoiceIntent.Cancel, intentFor(VoiceStyle.Ripple, dx = 0f, dy = -t - 1f, threshold = t))
        assertEquals(VoiceIntent.Send, intentFor(VoiceStyle.Ripple, dx = 0f, dy = -t + 1f, threshold = t))
        // 左右移动不该被豆包式误判成取消。
        assertEquals(VoiceIntent.Send, intentFor(VoiceStyle.Ripple, dx = -t - 100f, dy = 0f, threshold = t))
    }

    @Test
    fun `floating left cancels and right converts to text - not the other way round`() {
        assertEquals(VoiceIntent.Cancel, intentFor(VoiceStyle.Floating, dx = -t - 1f, dy = 0f, threshold = t))
        assertEquals(VoiceIntent.ToText, intentFor(VoiceStyle.Floating, dx = t + 1f, dy = 0f, threshold = t))
        assertEquals(VoiceIntent.Send, intentFor(VoiceStyle.Floating, dx = 0f, dy = 0f, threshold = t))
    }

    @Test
    fun `bar only cancels on a left swipe past the threshold`() {
        assertEquals(VoiceIntent.Cancel, intentFor(VoiceStyle.Bar, dx = -t - 1f, dy = 0f, threshold = t))
        assertEquals(VoiceIntent.Send, intentFor(VoiceStyle.Bar, dx = t + 1f, dy = -t - 1f, threshold = t))
    }
}
