package com.openapi.ks.myapp.dh

import com.openapi.ks.myapp.avatar.AvatarCommand

/**
 * Facade：把通话音频与协议指令接到当前引擎。
 * [swap] 先 release 旧引擎，避免 GL/协程泄漏。
 */
class DigitalHumanBridge(
    private var engine: DigitalHumanEngine,
) {
    fun current(): DigitalHumanEngine = engine

    fun swap(next: DigitalHumanEngine) {
        if (next === engine) return
        engine.release()
        engine = next
    }

    fun onAvatarCommand(cmd: AvatarCommand) {
        engine.dispatch(cmd)
    }

    fun onMouthLevel(level: Float) {
        engine.dispatch(AvatarCommand.Mouth(level))
    }

    /** Duix 等需要 PCM 时使用；P0 空实现。 */
    @Suppress("UNUSED_PARAMETER")
    fun onRemotePcm(pcm16leMono16k: ByteArray) {}
}
