package com.openapi.ks.myapp.companion.protocol

/**
 * 端侧降级阶梯 L0–L4（§12.3）。
 *
 * 纯 Kotlin、假时钟可测：所有时间由调用方以单调毫秒传入。判定只看本地传感器，
 * 不发新消息、不改形象渲染——接线层用 [level] 挡 AvatarJson、写 `telemetry.health`。
 *
 * 升档立刻；降档要连续好窗口 ≥ [hysteresisMs]，避免抖动。
 *
 * 静默类升档（L2 cue 静默 / L3 data 静默）只在「此刻确实期望 cmp 数据」时才评估：
 * 一条 speech 已声明未结束（[onSpeechExpected]），且远端音频不在播（[onAudio] 不新鲜）。
 * 陪伴通话里 cmp.* 只在回合边界前后有包，长句播放 15 秒、聆听 30 秒都静默是常态，
 * 而远端轨保活帧让 PCM 恒「活」——不加这道闸，安静对话会被误读成 L3，并自我强化
 * （服务端按自报档位闸掉 cue → cmp 更静 → 锁死 L3；2026-11-23 三通 job 实证）。
 */
class DegradeController(
    private val windowSize: Int = 20,
    private val cueSilenceMs: Long = 5_000L,
    private val dataDeadMs: Long = 6_000L,
    private val pcmAliveMs: Long = 2_000L,
    private val pcmDeadMs: Long = 6_000L,
    private val audioAliveMs: Long = 1_500L,
    private val hysteresisMs: Long = 2_500L,
    private val lateRatioThreshold: Double = 0.3,
    private val gapStreakThreshold: Int = 3,
) {
    enum class Conn { CONNECTED, RECONNECTING, DISCONNECTED }

    /** 手动注入（验收每一档 / 单测）。非 null 时压过自动判定。 */
    var forceLevel: Int? = null

    // 环形窗口：true = expired，false = applied。只计 decide 结果，不算 CANCELLED。
    private val decisions = BooleanArray(windowSize)
    private var decisionCount = 0
    private var decisionWrite = 0
    private var expiredInWindow = 0

    private var cueGapStreak = 0
    private var lastCueAt: Long? = null
    private var lastDataAt: Long? = null
    private var lastPcmAt: Long? = null
    private var lastAudioAt: Long? = null
    private var speechExpected = false
    private var connection: Conn = Conn.CONNECTED

    private var stickyLevel = 0
    private var recoverSince: Long? = null

    // AudioTrackSink + Default 协程都会摸传感器；对外方法全部串行化。
    @Synchronized
    fun onCueApplied(lateMs: Long) {
        pushDecision(expired = false)
    }

    @Synchronized
    fun onCueExpired() {
        pushDecision(expired = true)
    }

    @Synchronized
    fun onCueGap() {
        cueGapStreak++
    }

    /** cue 到达：刷新静默计时。无 gap 的干净到达才会清零 streak。 */
    @Synchronized
    fun onCueAccepted(now: Long, clearGapStreak: Boolean = true) {
        if (clearGapStreak) cueGapStreak = 0
        lastCueAt = now
        lastDataAt = now
    }

    @Synchronized
    fun onDataAccepted(now: Long) {
        lastDataAt = now
    }

    @Synchronized
    fun onPcm(now: Long) {
        lastPcmAt = now
    }

    /**
     * 这条通话此刻是否期望 cmp 数据：`speech.seg` 已声明、还没 end/cancel。
     * 静默类升档（L2/L3）只在这个窗口里评估——空闲对话的 cmp 静默是常态，不是降级。
     */
    @Synchronized
    fun onSpeechExpected(expected: Boolean) {
        speechExpected = expected
    }

    /**
     * 一帧**可听**的远端音频到了（她正在说话）。播放期间的 cmp 静默解释得通
     * ——cue 只在段边界发，长句中间本来就没有包——所以这段时间不做静默升档。
     */
    @Synchronized
    fun onAudio(now: Long) {
        lastAudioAt = now
    }

    @Synchronized
    fun onConnection(state: Conn) {
        connection = state
        if (state == Conn.CONNECTED) {
            // 重连后窗口从干净状态再计；sticky 仍走 hysteresis，避免立刻抖回。
            clearWindow()
            cueGapStreak = 0
        }
    }

    @Synchronized
    fun level(now: Long): Int {
        forceLevel?.let { return it.coerceIn(0, 4) }
        val desired = desiredLevel(now)
        if (desired > stickyLevel) {
            stickyLevel = desired
            recoverSince = null
            return stickyLevel
        }
        if (desired < stickyLevel) {
            val since = recoverSince
            if (since == null) {
                recoverSince = now
                return stickyLevel
            }
            if (now - since >= hysteresisMs) {
                stickyLevel = desired
                recoverSince = null
                return stickyLevel
            }
            return stickyLevel
        }
        recoverSince = null
        return stickyLevel
    }

    private fun desiredLevel(now: Long): Int {
        if (connection == Conn.RECONNECTING || connection == Conn.DISCONNECTED) return 4

        val pcmAt = lastPcmAt
        if (pcmAt != null && now - pcmAt > pcmDeadMs) return 4

        // 静默类升档（L3 data 死 / L2 cue 静默）只在「此刻确实期望 cmp 数据」时评估：
        // 一条 speech 已声明未结束，且远端音频不在播。空闲对话 cmp 静默是常态；
        // 播放期间 cue 只在段边界发、长句中间本来就没包。不加这道闸，安静的陪伴通话
        // 会被读成 L3（PCM 保活帧让它恒「活」），并且自我强化：服务端按自报档位
        // 闸掉 cue → cmp 更静 → 锁死。2026-11-23 三通 job 实证。
        if (!silenceUpgradeArmed(now)) {
            if (lateRatio() > lateRatioThreshold || cueGapStreak >= gapStreakThreshold) return 1
            return 0
        }

        // L3：data 死且 PCM 尚未判死。pcmAliveMs 只作「仍在出声」的宽松提示，
        // 不再在 (alive, dead] 留死区——那段以前会掉到 L2。
        val dataAt = lastDataAt
        if (dataAt != null && now - dataAt > dataDeadMs && pcmAt != null) return 3

        val cueAt = lastCueAt
        if (cueAt != null && now - cueAt > cueSilenceMs) return 2

        if (lateRatio() > lateRatioThreshold || cueGapStreak >= gapStreakThreshold) return 1

        return 0
    }

    /** 静默类升档的闸：期望 cmp 数据（speech 已声明未结束），且没有可听帧在播。 */
    private fun silenceUpgradeArmed(now: Long): Boolean {
        if (!speechExpected) return false
        val audioAt = lastAudioAt ?: return true
        return now - audioAt > audioAliveMs
    }

    private fun lateRatio(): Double {
        if (decisionCount == 0) return 0.0
        return expiredInWindow.toDouble() / decisionCount.toDouble()
    }

    private fun pushDecision(expired: Boolean) {
        if (decisionCount == windowSize) {
            if (decisions[decisionWrite]) expiredInWindow--
        } else {
            decisionCount++
        }
        decisions[decisionWrite] = expired
        if (expired) expiredInWindow++
        decisionWrite = (decisionWrite + 1) % windowSize
    }

    private fun clearWindow() {
        decisionCount = 0
        decisionWrite = 0
        expiredInWindow = 0
    }

    companion object {
        /**
         * Apply 闸：L0 全放；L1 `state` 全放、`emotion` 约 1/2、其它丢；
         * L2+ 全部 `presentation.*` 丢。口型不经这条路径。
         */
        fun shouldEmit(eventType: String, level: Int, emotionSeq: Int): Boolean {
            if (level <= 0) return true
            if (level >= 2) return false
            // L1
            return when (eventType) {
                "presentation.state" -> true
                "presentation.emotion" -> emotionSeq % 2 == 0
                else -> false
            }
        }

        /**
         * B3：进入 L2 的**那条上升沿**要清一次当前表情。
         *
         * L0/L1 期间表情还会被后续 cue 改掉；L2+ 全部 presentation cue 被丢，
         * 中途换上的表情再也等不到覆盖它的下一条 emotion——只能在进档瞬间主动
         * 收回立绘。只在跨越 2 的边沿清一次：L2→L3 或 L2 期间重复清，只会在
         * 恢复期把刚挂上的新表情误抹掉。
         */
        fun shouldClearEmotion(prevLevel: Int, level: Int): Boolean =
            prevLevel < 2 && level >= 2
    }
}
