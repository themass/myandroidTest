package com.openapi.ks.myapp.companion.protocol

/**
 * 催话去重闸（C7 一键两义的「空闲」侧）。
 *
 * 空闲时按举手键 = 催她主动说话（OLLMV `use-footer.ts:41-43` 的 `ai-speak-signal`）。
 * 一次催话真实触发一轮 LLM+TTS：不去重就是连点烧推理。三层挡法，全部纯算术：
 *
 * 1. 她在说时调用方走打断，不该进闸（[onTap] 的 `agentSpeaking` 兜底再挡一次）；
 * 2. 冷却窗挡住连点；
 * 3. **回合窗口**：催出去之后 her 的回合一旦开始（`speech.seg` 到达），
 *    后续按键吞掉直到回合结束。这层比冷却窗长，专挡「催了、她在酝酿、又点一下」。
 *    去重信号是回合的开始/结束本身，不另起计时器——OLLMV 的 `interrupted` 粘滞态
 *    就是旁路计时器堆出来的反面教材。
 */
class NudgeGate(private val cooldownMs: Long = 1_500L) {

    enum class State { IDLE, COOLDOWN, AWAITING_REPLY }

    var state: State = State.IDLE
        private set

    private var lastNudgeAt: Long = 0L

    /** 催已发出、还没等到回合开始：`speech.seg` 到达时把它升成 AWAITING_REPLY。 */
    private var pendingNudge: Boolean = false

    /**
     * 用户按了举手键（空闲分支）。
     *
     * @return true = 该发一条催话；false = 被挡（她在说 / 冷却中 / 回合在飞）。
     */
    fun onTap(agentSpeaking: Boolean, now: Long): Boolean {
        if (agentSpeaking) return false
        if (state == State.AWAITING_REPLY) return false
        if (state == State.COOLDOWN && now - lastNudgeAt < cooldownMs) return false
        state = State.COOLDOWN
        lastNudgeAt = now
        pendingNudge = true
        return true
    }

    /** 一条催话换来了回合开始（`speech.seg`）。不是催换来的回合不改变状态。 */
    fun onReplyStarted(now: Long) {
        if (pendingNudge) {
            pendingNudge = false
            state = State.AWAITING_REPLY
        }
    }

    /** 回合结束（`speech.end` / `control.cancel` / 断开）。只在等待回执时生效。 */
    fun onReplyEnded(now: Long) {
        if (state == State.AWAITING_REPLY) state = State.COOLDOWN
    }

    /** 重连 / 挂断：清状态，不跨通话。 */
    fun reset() {
        state = State.IDLE
        lastNudgeAt = 0L
        pendingNudge = false
    }
}
