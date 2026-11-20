package com.openapi.ks.myapp.dh

/**
 * 远端 PCM 静音闸门。
 *
 * Duix 这类引擎「收到 PCM 就一直驱动口型+头部」，而 LiveKit 的音频回调在 AI 静音时
 * 仍以 ~10ms 节拍推近零块（comfort noise），结果静默态人物也在小幅摆动，像在小声说话。
 * 闸门只放行有声块：峰值低于 [FLOOR] 的块丢掉不推；最后一块有声之后仍继续放行
 * [HANGOVER_MS]（防止轻音尾音被掐掉），静音超过这个时长判定「这段说完了」，报一次
 * [Decision.END]——引擎收到 END 要 flush 收尾（stopPush）让嘴闭合、回到待机视频循环，
 * 之后维持 SKIP 不再推，人物完全停回待机。
 *
 * 纯逻辑 + 注入时钟，JVM 测试钉住。
 */
internal class PcmGate(
    private val floor: Float = FLOOR,
    private val hangoverMs: Long = HANGOVER_MS,
) {
    enum class Decision {
        /** 推给引擎（有声块，或尾段余量内的块）。 */
        PUSH,

        /** 静音块：丢掉，不推。 */
        SKIP,

        /** 刚判定这段说完（尾段余量用完、仍在静音）：引擎应 flush 收尾。 */
        END,
    }

    /** 最近一次「有声」的时间戳（ms）；null = 当前不在说话段内。 */
    private var lastVoicedAtMs: Long? = null

    /**
     * 喂一块 16k mono PCM16LE，[nowMs] 用单调时钟（elapsedRealtime）。
     * END 只在静音跨过量身的那一刻报一次，之后回到 SKIP，不会每块都报。
     */
    fun feed(pcm: ByteArray, nowMs: Long): Decision {
        if (peakLevel(pcm) >= floor) {
            lastVoicedAtMs = nowMs
            return Decision.PUSH
        }
        val voicedAt = lastVoicedAtMs ?: return Decision.SKIP
        return if (nowMs - voicedAt >= hangoverMs) {
            lastVoicedAtMs = null
            Decision.END
        } else {
            Decision.PUSH
        }
    }

    companion object {
        /** 峰值绝对值 < 满幅 2%（约 -34 dBFS）视为静音：AI 静默时的 comfort noise 远低于此。 */
        const val FLOOR = 0.02f

        /** 尾段余量：词与词的自然停顿不切断，真正的句尾 400ms 后收尾。 */
        const val HANGOVER_MS = 400L

        /** PCM16LE 块的峰值绝对值，归一化 0..1。 */
        fun peakLevel(pcm: ByteArray): Float {
            var peak = 0
            var i = 0
            while (i + 1 < pcm.size) {
                val s = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xff)).toShort().toInt()
                val a = if (s < 0) -s else s
                if (a > peak) peak = a
                i += 2
            }
            return peak / 32768f
        }
    }
}

/**
 * [PcmGate] 与引擎推送调用之间的接线：喂块 → 闸门 → push / flush。
 * 时钟与两个回调注入，JVM 测试可完全驱动，不碰 Android 与 SDK。
 */
internal class RemotePcmPump(
    private val gate: PcmGate = PcmGate(),
    private val nowMs: () -> Long,
    private val push: (ByteArray) -> Unit,
    private val flush: () -> Unit,
) {
    fun feed(pcm: ByteArray) {
        when (gate.feed(pcm, nowMs())) {
            PcmGate.Decision.PUSH -> push(pcm)
            PcmGate.Decision.SKIP -> Unit
            PcmGate.Decision.END -> flush()
        }
    }

    /** 外部已知「这段完了」（如 rig 播完一个文件）：直接 flush，不等闸门量完余量。 */
    fun end() = flush()
}
