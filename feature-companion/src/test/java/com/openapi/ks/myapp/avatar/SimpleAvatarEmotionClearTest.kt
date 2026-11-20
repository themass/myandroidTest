package com.openapi.ks.myapp.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * B3：降级进 L2 后要能把卡在脸上的表情收回立绘。
 *
 * L2+ 端侧丢全部 presentation cue，所以中途换上的表情图永远等不到下一条
 * emotion——角色卡里它是笑着的，屏幕上她就一直笑着，哪怕语音早就断了。
 * `AvatarEventMapper.mapEmotion("")` 钉死返回 null（「emotion 为空」≠「换表情」），
 * 所以清除必须是指令链上的一个显式动作，而不是塞一条空 emotion。
 */
class SimpleAvatarEmotionClearTest {

    private fun rendererWithExpressions(): SimpleAvatarRenderer =
        SimpleAvatarRenderer().apply {
            bind(
                AvatarSpec(
                    portraitRes = 1,
                    expressions = mapOf("joy" to 2),
                    files = AvatarFiles(portrait = File("p.png"), expressions = mapOf("joy" to File("joy.png"))),
                ),
            )
        }

    @Test
    fun `ClearEmotion drops a set expression back to the portrait`() {
        val renderer = rendererWithExpressions()
        renderer.dispatch(AvatarCommand.Emotion("joy"))
        assertEquals(2, renderer.expressionRes.value)
        assertEquals(File("joy.png"), renderer.expressionFile.value)

        renderer.dispatch(AvatarCommand.ClearEmotion)
        assertNull("资源通道要回立绘", renderer.expressionRes.value)
        assertNull("文件通道也要回立绘", renderer.expressionFile.value)
    }

    @Test
    fun `ClearEmotion on a fresh renderer is a harmless no-op`() {
        val renderer = rendererWithExpressions()
        renderer.dispatch(AvatarCommand.ClearEmotion)
        assertNull(renderer.expressionRes.value)
        assertNull(renderer.expressionFile.value)
    }
}
