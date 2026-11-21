package com.openapi.ks.myapp.dh

import com.openapi.ks.myapp.avatar.AvatarCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * VrmBridge 纯函数钉桩：AvatarCommand / 拖动 → VRM 场景指令，以及发进场景页的 JSON 线格式。
 * 场景页（assets/dh-vrm/index.html）按这个线格式实现，改动必须两边同步。
 */
class VrmBridgeTest {

    @Test
    fun emotion_joy_maps_to_vrm_happy_expression() {
        assertEquals(
            VrmSceneCommand.Expression("happy"),
            VrmBridge.sceneCommand(AvatarCommand.Emotion("joy")),
        )
    }

    @Test
    fun emotion_without_vrm_equivalent_keeps_current_expression() {
        // 协议语义：没映射就保持当前表情（同 Live2D 适配器），不能乱换。
        assertNull(VrmBridge.sceneCommand(AvatarCommand.Emotion("disgusted")))
    }

    @Test
    fun clear_emotion_returns_face_to_neutral() {
        assertEquals(
            VrmSceneCommand.Expression("neutral"),
            VrmBridge.sceneCommand(AvatarCommand.ClearEmotion),
        )
    }

    @Test
    fun mouth_openness_clamps_to_vrm_range() {
        assertEquals(VrmSceneCommand.Mouth(1f), VrmBridge.sceneCommand(AvatarCommand.Mouth(1.5f)))
        assertEquals(VrmSceneCommand.Mouth(0f), VrmBridge.sceneCommand(AvatarCommand.Mouth(-0.2f)))
    }

    @Test
    fun drag_maps_to_clamped_look_target() {
        assertEquals(VrmSceneCommand.Look(0.6f, -0.4f), VrmBridge.lookFromDrag(0.6f, -0.4f))
        assertEquals(VrmSceneCommand.Look(1f, -1f), VrmBridge.lookFromDrag(3f, -9f))
    }

    @Test
    fun scene_commands_encode_to_json_line_format() {
        assertEquals(
            """{"type":"expression","name":"happy"}""",
            VrmBridge.encode(VrmSceneCommand.Expression("happy")),
        )
        assertEquals(
            """{"type":"mouth","openness":0.4}""",
            VrmBridge.encode(VrmSceneCommand.Mouth(0.4f)),
        )
        assertEquals(
            """{"type":"look","x":0.5,"y":-0.25}""",
            VrmBridge.encode(VrmSceneCommand.Look(0.5f, -0.25f)),
        )
    }
}
