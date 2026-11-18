package com.openapi.ks.myapp.companion.protocol

/**
 * 本地打断闸（§10.1）：她在说 + 你开始出声 → 触发一次 barge_in。
 *
 * 纯状态机、不碰 LiveKit：上升沿 + 冷却。冷却挡住 VAD 抖动；上升沿避免
 * 持续说话时每帧都发。没有活跃 speech 时不触发——本地没什么可作废的，
 * 服务端 interrupt 空转也没意义。
 */
class BargeInGate(
    private val cooldownMs: Long = 400L,
) {
    private var lastAt: Long = 0L
    private var wasSpeaking: Boolean = false

    /**
     * @param selfLevel 本地采集归一化电平（与 [LiveKitCompanionTransport] 的 selfLevel 同量纲）
     * @param noiseGate 噪声门；低于此视为没开口
     * @param activeSpeechId 当前 PENDING/PLAYING 的 speech；没有就不打断
     * @return 该打断的 speech_id；本帧不该打断则 null
     */
    fun onSelfLevel(
        selfLevel: Float,
        noiseGate: Float,
        activeSpeechId: String?,
        now: Long,
    ): String? {
        val speaking = selfLevel > noiseGate
        val rising = speaking && !wasSpeaking
        wasSpeaking = speaking
        if (!rising) return null
        val id = activeSpeechId ?: return null
        if (cooldownMs > 0L && now - lastAt < cooldownMs) return null
        lastAt = now
        return id
    }

    /** 重连 / 挂断时清状态，避免下一通把上一通的冷却带过去。 */
    fun reset() {
        lastAt = 0L
        wasSpeaking = false
    }
}
