package com.openapi.ks.myapp.dh

import com.openapi.ks.myapp.avatar.AvatarCommand
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 页面就绪前的指令排队/冲刷：桥接只认「页面 ready 后才能 evaluateJavascript」这一条时序。
 */
class VrmSceneControllerTest {

    @Test
    fun commands_before_page_ready_flush_in_order_once_ready() {
        val sent = mutableListOf<String>()
        val c = VrmSceneController(send = { sent += it })

        c.onCommand(AvatarCommand.Emotion("joy"))
        c.onCommand(AvatarCommand.Mouth(0.4f))
        assertEquals(emptyList<String>(), sent)

        c.onPageReady()
        assertEquals(
            listOf(
                """window.vrmCommand('{"type":"expression","name":"happy"}')""",
                """window.vrmCommand('{"type":"mouth","openness":0.4}')""",
            ),
            sent,
        )
    }

    @Test
    fun commands_after_ready_send_through_immediately() {
        val sent = mutableListOf<String>()
        val c = VrmSceneController(send = { sent += it })
        c.onPageReady()

        c.onCommand(AvatarCommand.Gaze(0.5f, -0.25f))
        assertEquals(listOf("""window.vrmCommand('{"type":"look","x":0.5,"y":-0.25}')"""), sent)
    }

    @Test
    fun commands_without_vrm_mapping_are_dropped() {
        val sent = mutableListOf<String>()
        val c = VrmSceneController(send = { sent += it })
        c.onPageReady()

        c.onCommand(AvatarCommand.Gesture("wave"))
        c.onCommand(AvatarCommand.SetState(com.openapi.ks.myapp.avatar.AvatarState.SPEAKING))
        assertEquals(emptyList<String>(), sent)
    }
}
