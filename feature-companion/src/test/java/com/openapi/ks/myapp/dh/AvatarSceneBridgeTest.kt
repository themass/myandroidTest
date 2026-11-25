package com.openapi.ks.myapp.dh

import com.openapi.ks.myapp.avatar.AvatarCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 官方引擎场景桥（Spine / TalkingHead 的 WebView 页）：线格式与页面侧 `window.dhCommand`
 * 按同一 JSON 约定，改动必须两边同步（同 [VrmBridge] 的纪律）。
 */
class AvatarSceneBridgeTest {

    @Test
    fun `线格式 - mouth 保留小数`() {
        assertEquals(
            """{"type":"mouth","openness":0.7}""",
            AvatarSceneBridge.encode(AvatarSceneCommand.Mouth(0.7f)),
        )
    }

    @Test
    fun `线格式 - expression 与 look 与 animation`() {
        assertEquals("""{"type":"expression","name":"happy"}""", AvatarSceneBridge.encode(AvatarSceneCommand.Expression("happy")))
        assertEquals("""{"type":"look","x":-0.5,"y":0.25}""", AvatarSceneBridge.encode(AvatarSceneCommand.Look(-0.5f, 0.25f)))
        assertEquals("""{"type":"animation","name":"walk"}""", AvatarSceneBridge.encode(AvatarSceneCommand.Animation("walk")))
    }

    @Test
    fun `jsCall 用单引号包 JSON 并转义反斜杠和单引号`() {
        val call = AvatarSceneBridge.jsCall(AvatarSceneCommand.Expression("it's"))
        assertEquals("""window.dhCommand('{"type":"expression","name":"it\'s"}')""", call)
    }

    @Test
    fun `Spine 桥 - 词表内手势映射官方动作，其余一律不发声`() {
        val bridge = SpineSceneBridge()
        assertEquals(
            AvatarSceneCommand.Animation("walk"),
            bridge.sceneCommand(AvatarCommand.Gesture("walk")),
        )
        // 没映射的动作绝不发：spineboy 没有 pat_head，硬映射会演错戏。
        assertNull(bridge.sceneCommand(AvatarCommand.Gesture("pat_head")))
        assertNull(bridge.sceneCommand(AvatarCommand.Gesture("")))
        assertNull(bridge.sceneCommand(AvatarCommand.Mouth(0.5f)))
        assertNull(bridge.sceneCommand(AvatarCommand.Emotion("joy")))
    }

    @Test
    fun `Spine 桥 - 词表大小写与空白容错`() {
        assertEquals(
            AvatarSceneCommand.Animation("walk"),
            SpineSceneBridge().sceneCommand(AvatarCommand.Gesture(" WALK ")),
        )
    }

    @Test
    fun `TalkingHead 桥 - mouth 夹取 emotion 映射 gaze 直传`() {
        val bridge = TalkingHeadSceneBridge()
        assertEquals(AvatarSceneCommand.Mouth(1.0f), bridge.sceneCommand(AvatarCommand.Mouth(1.7f)))
        assertEquals(AvatarSceneCommand.Expression("happy"), bridge.sceneCommand(AvatarCommand.Emotion("joy")))
        assertEquals(AvatarSceneCommand.Expression("fear"), bridge.sceneCommand(AvatarCommand.Emotion("surprised")))
        assertEquals(AvatarSceneCommand.Look(0.3f, -0.4f), bridge.sceneCommand(AvatarCommand.Gaze(0.3f, -0.4f)))
    }

    @Test
    fun `TalkingHead 桥 - 未映射情绪保持当前表情，ClearEmotion 回 neutral`() {
        val bridge = TalkingHeadSceneBridge()
        // TalkingHead 内置 mood 只有 neutral/happy/angry/sad/fear/disgust——
        // 词表外的（如 shy）不发指令 = 保持当前，与 VrmBridge 同一语义。
        assertNull(bridge.sceneCommand(AvatarCommand.Emotion("shy")))
        assertEquals(
            AvatarSceneCommand.Expression("neutral"),
            bridge.sceneCommand(AvatarCommand.ClearEmotion),
        )
    }
}
