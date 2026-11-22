package com.openapi.ks.myapp.dh

import com.openapi.ks.myapp.avatar.AvatarCommand
import com.openapi.ks.myapp.avatar.AvatarTouch
import com.openapi.ks.myapp.avatar.TouchResult

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

    /**
     * 切换时先 release 旧引擎，再让 [factory] 创建新的——避免双 GL/双协程重叠。
     * 切到同一实例时 no-op。
     */
    fun replaceWith(factory: () -> DigitalHumanEngine): DigitalHumanEngine {
        engine.release()
        val next = factory()
        engine = next
        return next
    }

    fun onAvatarCommand(cmd: AvatarCommand) {
        engine.dispatch(cmd)
    }

    /**
     * 舞台认出的手指事件 → 当前引擎（触点互动的唯一入口，切换后自动跟到新引擎）。
     * 返回值留给调用方做本地反馈判断；不认识的引擎回 [TouchResult.Ignored]。
     */
    fun onAvatarTouch(event: AvatarTouch): TouchResult = engine.onTouch(event)

    fun onMouthLevel(level: Float) {
        engine.dispatch(AvatarCommand.Mouth(level))
    }

    /** Duix 等需要 PCM 时转发到当前引擎；其它引擎默认空实现。 */
    fun onRemotePcm(pcm16leMono16k: ByteArray) {
        engine.pushRemotePcm(pcm16leMono16k)
    }

    /** 「这段说完了」：让当前引擎结束 PCM 推送段收尾；其它引擎默认空实现。 */
    fun flushRemotePcm() {
        engine.flushRemotePcm()
    }
}
