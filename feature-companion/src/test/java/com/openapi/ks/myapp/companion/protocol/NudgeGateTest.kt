package com.openapi.ks.myapp.companion.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 催话去重闸（C7 一键两义的空闲语义）。
 *
 * 空闲时按举手键 = 催她主动说话（OLLMV `use-footer.ts:41-43` 的 `ai-speak-signal`）。
 * 催话会真实触发一轮 LLM+TTS，不去重就是按一次烧一轮推理、按五次烧五轮：
 * - 她在说时不该催（那是打断的语义，调用方走另一条路）；
 * - 连点只生效一次（冷却）；
 * - 催过之后她的回合已经开始了，就不该再催——回合的开始是去重信号，
 *   不靠第二个计时器（OLLMV 的 `interrupted` 粘滞态就是计时器旁路堆出来的反面教材）。
 */
class NudgeGateTest {

    private val gate = NudgeGate(cooldownMs = 1_500)

    @Test
    fun `idle tap nudges`() {
        assertTrue(gate.onTap(agentSpeaking = false, now = 1_000))
    }

    @Test
    fun `while she is speaking a nudge is refused`() {
        assertFalse(gate.onTap(agentSpeaking = true, now = 1_000))
    }

    @Test
    fun `a second tap inside the cooldown is refused`() {
        assertTrue(gate.onTap(agentSpeaking = false, now = 1_000))
        assertFalse(gate.onTap(agentSpeaking = false, now = 1_400))
        // 冷却过了才允许下一次（用户真的想再催一次）。
        assertTrue(gate.onTap(agentSpeaking = false, now = 2_501))
    }

    /**
     * 催出去之后，服务端回合一旦开始（回复在流式了）就吞掉后续按键——
     * 这挡的是「催了、她在酝酿、用户又点一下」的重复回合，比冷却窗更长。
     */
    @Test
    fun `an expected reply in flight swallows further nudges until it ends`() {
        assertTrue(gate.onTap(agentSpeaking = false, now = 1_000))
        gate.onReplyStarted(now = 1_100)
        // 回合结束早已过了冷却，但回合刚开始这一阵不能重复催。
        assertFalse(gate.onTap(agentSpeaking = false, now = 1_200))
        gate.onReplyEnded(now = 9_000)
        assertTrue(gate.onTap(agentSpeaking = false, now = 9_001))
    }

    @Test
    fun `reply window does not block when no nudge was pending`() {
        // 她因别的原因开口（用户先说话了）：回合信号不该把之后的催话永久挡住。
        gate.onReplyStarted(now = 1_000)
        gate.onReplyEnded(now = 5_000)
        assertTrue(gate.onTap(agentSpeaking = false, now = 5_100))
    }

    @Test
    fun `reset clears cooldown and the pending expectation`() {
        gate.onTap(agentSpeaking = false, now = 1_000)
        gate.onReplyStarted(now = 1_100)
        gate.reset()
        assertEquals(NudgeGate.State.IDLE, gate.state)
        assertTrue(gate.onTap(agentSpeaking = false, now = 1_200))
    }
}
