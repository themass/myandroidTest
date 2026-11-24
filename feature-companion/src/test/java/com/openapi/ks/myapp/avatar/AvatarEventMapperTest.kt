package com.openapi.ks.myapp.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 协议 → 指令的唯一转换点。这个文件以前不存在（cue 链路的覆盖全靠
 * PresentationCueTest 里那条正则锁），而 ViewModel 在链路上另写了一份正则——
 * 两处解析迟早漂移。收敛到 mapper 之前，先把它的行为钉死。
 */
class AvatarEventMapperTest {

    // ---- mapJson：既有行为，特征测试钉住 ----

    @Test
    fun `emotion event maps to lowercased Emotion command`() {
        assertEquals(
            AvatarCommand.Emotion("joy"),
            AvatarEventMapper.mapJson("""{"v":1,"type":"emotion","emotion":"Joy"}"""),
        )
    }

    @Test
    fun `state event maps to SetState`() {
        assertEquals(
            AvatarCommand.SetState(AvatarState.SPEAKING),
            AvatarEventMapper.mapJson("""{"v":1,"type":"state","state":"SPEAKING"}"""),
        )
    }

    @Test
    fun `empty emotion value means no emotion, not a command`() {
        // agent 偶尔发 "emotion":"" 表示「没有情绪」，不能变成 Emotion("")。
        assertNull(AvatarEventMapper.mapJson("""{"type":"emotion","emotion":""}"""))
    }

    @Test
    fun `unknown event type is ignored, never crashes`() {
        assertNull(AvatarEventMapper.mapJson("""{"type":"gaze","x":0.5}"""))
        assertNull(AvatarEventMapper.mapJson("""{"state":"idle"}"""))
        assertNull(AvatarEventMapper.mapJson("""{"""))
    }

    // ---- mapState / mapEmotion：UI state 字符串直连，替掉反包 JSON 的绕路 ----

    @Test
    fun `mapState accepts the ui state strings the transport publishes`() {
        assertEquals(AvatarCommand.SetState(AvatarState.LISTENING), AvatarEventMapper.mapState("listening"))
        assertEquals(AvatarCommand.SetState(AvatarState.THINKING), AvatarEventMapper.mapState("THINKING"))
        assertEquals(AvatarCommand.SetState(AvatarState.IDLE), AvatarEventMapper.mapState("idle"))
        assertEquals(AvatarCommand.SetState(AvatarState.SPEAKING), AvatarEventMapper.mapState("speaking"))
        // P1 契约：interrupted 是端侧 barge-in 乐观写入的状态，必须一路走到渲染器。
        assertEquals(AvatarCommand.SetState(AvatarState.INTERRUPTED), AvatarEventMapper.mapState("interrupted"))
    }

    @Test
    fun `mapState rejects unknown and missing without inventing idle`() {
        // 未知状态返回 null 比兜底成 IDLE 诚实：宁可形象停在当前状态，
        // 也不要为一个没听过的词播一次「清空表情 + 闭嘴」。
        assertNull(AvatarEventMapper.mapState("microphoning"))
        assertNull(AvatarEventMapper.mapState(null))
        assertNull(AvatarEventMapper.mapState(""))
    }

    @Test
    fun `mapEmotion normalizes case and blank like mapJson does`() {
        assertEquals(AvatarCommand.Emotion("shy"), AvatarEventMapper.mapEmotion("Shy"))
        assertNull(AvatarEventMapper.mapEmotion(null))
        assertNull(AvatarEventMapper.mapEmotion(""))
    }

    // ---- emotionCommand：UI state → 渲染器指令，null 也是指令（B3）----

    @Test
    fun `emotionCommand turns a named emotion into Emotion like mapEmotion`() {
        assertEquals(AvatarCommand.Emotion("shy"), AvatarEventMapper.emotionCommand("Shy"))
    }

    @Test
    fun `emotionCommand answers null with an explicit clear, not with nothing`() {
        // mapEmotion(null) 返回 null 是对的：「emotion 缺失」不该变成一条指令。
        // 但 UI state 走到 null（比如降级进 L2 后主动清场）时，界面上那张还挂着的
        // 表情图必须被收回——「没有 emotion」和「不用改画面」是两回事。
        assertEquals(AvatarCommand.ClearEmotion, AvatarEventMapper.emotionCommand(null))
        assertEquals(AvatarCommand.ClearEmotion, AvatarEventMapper.emotionCommand(""))
    }
}
