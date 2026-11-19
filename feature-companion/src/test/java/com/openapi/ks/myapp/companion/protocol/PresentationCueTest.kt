package com.openapi.ks.myapp.companion.protocol

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `cmp.cue` → v1 事件形状的翻译（§11.1）。
 *
 * 这里锁的是**键名**：v2 的情绪键叫 `name`，v1 叫 `emotion`。写错了不会报错，
 * 只会让每一帧情绪都静默变成 neutral——表现是「她表情一直是平静的」，而两端日志
 * 都是干净的。所以键名必须有测试，不能靠读代码确认。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PresentationCueTest {

    @Test
    fun `name 取到的情绪被认出来`() {
        val emotion = PresentationCue.parseEmotion(JSONObject().put("name", "joy"))!!
        assertEquals("joy", emotion.name)
        assertTrue(emotion.known)
    }

    @Test
    fun `v1 的 emotion 键不被当成 v2 的 name`() {
        // 反过来（v1 形状的包走到这里）应当当作「没有情绪名」丢掉，而不是猜。
        assertNull(PresentationCue.parseEmotion(JSONObject().put("emotion", "joy")))
    }

    @Test
    fun `不认识的情绪映射成 neutral 而不是原样透传`() {
        val emotion = PresentationCue.parseEmotion(JSONObject().put("name", "欢天喜地"))!!
        assertEquals("neutral", emotion.name)
        assertFalse(emotion.known)
    }

    @Test
    fun `大小写与空白被归一`() {
        val emotion = PresentationCue.parseEmotion(JSONObject().put("name", "  JOY "))!!
        assertEquals("joy", emotion.name)
        assertTrue(emotion.known)
    }

    @Test
    fun `空名字不触发任何表情`() {
        assertNull(PresentationCue.parseEmotion(JSONObject().put("name", "")))
        assertNull(PresentationCue.parseEmotion(JSONObject().put("name", "   ")))
        assertNull(PresentationCue.parseEmotion(JSONObject()))
    }

    @Test
    fun `intensity 不影响解析`() {
        // 渲染器没有「半强度」的表达：多出来的键丢掉即可，不该让这条 cue 失败。
        val emotion = PresentationCue.parseEmotion(JSONObject().put("name", "sad").put("intensity", 0.4))!!
        assertEquals("sad", emotion.name)
    }

    @Test
    fun `状态取值转小写`() {
        assertEquals("listening", PresentationCue.parseState(JSONObject().put("state", "LISTENING")))
        assertNull(PresentationCue.parseState(JSONObject().put("state", "")))
        assertNull(PresentationCue.parseState(JSONObject()))
    }

    @Test
    fun `翻译出来的串是 v1 的 key 且能被 ViewModel 的正则认出来`() {
        val emotionEvent = PresentationCue.emotionEventJson("joy")
        assertTrue(emotionEvent.contains("\"type\":\"emotion\""))
        assertTrue(emotionEvent.contains("\"emotion\":\"joy\""))
        assertFalse(emotionEvent.contains("\\u"))

        val stateEvent = PresentationCue.stateEventJson("speaking")
        assertTrue(stateEvent.contains("\"type\":\"state\""))
        assertTrue(stateEvent.contains("\"state\":\"speaking\""))
    }

    @Test
    fun `中文情绪名在事件串里不被转义`() {
        // 这里进了 unknown 分支，但 quote 的逻辑是同一个：中文必须按 UTF-8 走。
        val emotion = PresentationCue.parseEmotion(JSONObject().put("name", "开心"))!!
        assertEquals("neutral", emotion.name)
        assertFalse(PresentationCue.stateEventJson("开心").contains("\\u"))
    }

    @Test
    fun `gesture 优先取 id 并接受 name 回落`() {
        assertEquals("nod", PresentationCue.parseGesture(JSONObject().put("id", "Nod")))
        assertEquals("tilt_head", PresentationCue.parseGesture(JSONObject().put("name", "Tilt_Head")))
        assertEquals("nod", PresentationCue.parseGesture(JSONObject().put("id", "nod").put("name", "other")))
        assertNull(PresentationCue.parseGesture(JSONObject()))
    }

    @Test
    fun `gaze 可按名字或数值解析`() {
        val named = PresentationCue.parseGaze(JSONObject().put("name", "look_left"))!!
        assertEquals("look_left", named.name)
        assertEquals(-0.5f, named.x, 0.0001f)
        assertEquals(0f, named.y, 0.0001f)

        val numeric = PresentationCue.parseGaze(JSONObject().put("x", 0.2).put("y", -0.1))!!
        assertEquals("custom", numeric.name)
        assertEquals(0.2f, numeric.x, 0.0001f)
        assertEquals(-0.1f, numeric.y, 0.0001f)

        assertNull(PresentationCue.parseGaze(JSONObject()))
    }

    @Test
    fun `gesture 与 gaze 事件串能被 mapper 识别`() {
        val gesture = PresentationCue.gestureEventJson("nod")
        assertTrue(gesture.contains("\"type\":\"gesture\""))
        assertTrue(gesture.contains("\"id\":\"nod\""))

        val gaze = PresentationCue.gazeEventJson("look_user", 0f, 0f)
        assertTrue(gaze.contains("\"type\":\"gaze\""))
        assertTrue(gaze.contains("\"name\":\"look_user\""))
    }
}
