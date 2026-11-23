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

    @Test
    fun orbit_deltas_accumulate_into_absolute_yaw() {
        // 手势层发的是**增量**（Compose 每帧只有 dx），控制器累加成绝对角——
        // 页面直接写 scene.rotation.y，掉一条指令只丢一段转动，不会永久错位。
        val sent = mutableListOf<String>()
        val c = VrmSceneController(send = { sent += it })
        c.onPageReady()

        c.onOrbitDelta(90f)
        c.onOrbitDelta(90f)
        assertEquals(
            """window.vrmCommand('{"type":"orbit","yawDeg":180.0}')""",
            sent.last(),
        )
    }

    @Test
    fun queued_orbits_collapse_to_the_accumulated_yaw() {
        // 页面 ready 前攒下的拖动只发最后累计值：中间态发出去只会让角色
        // 补一段倒退再前进的抽搐。
        val sent = mutableListOf<String>()
        val c = VrmSceneController(send = { sent += it })

        c.onOrbitDelta(40f)
        c.onOrbitDelta(55f)
        c.onOrbitDelta(115f)
        assertEquals(emptyList<String>(), sent)

        c.onPageReady()
        assertEquals(listOf("""window.vrmCommand('{"type":"orbit","yawDeg":210.0}')"""), sent)
    }

    @Test
    fun orbit_full_turn_is_not_clamped_and_does_not_disturb_expression_queue() {
        val sent = mutableListOf<String>()
        val c = VrmSceneController(send = { sent += it })
        c.onPageReady()

        // 水平转一圈是验收项：6 段 60° 累到 360° 原样出，不折半不夹取。
        repeat(6) { c.onOrbitDelta(60f) }
        assertEquals("""window.vrmCommand('{"type":"orbit","yawDeg":360.0}')""", sent.last())

        // 表情仍按序直通（合并只发生在未冲刷的 orbit 之间）。
        c.onCommand(AvatarCommand.Emotion("joy"))
        assertEquals(
            """window.vrmCommand('{"type":"expression","name":"happy"}')""",
            sent.last(),
        )
    }
}
