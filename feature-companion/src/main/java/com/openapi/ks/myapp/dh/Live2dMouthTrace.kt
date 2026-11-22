package com.openapi.ks.myapp.dh

/**
 * 口型轨迹日志的节流门（纯函数，JVM 可测）——真机验收的证据通道。
 *
 * 嘴型电平 20ms 一帧，全量打日志一句话几百行；这里约定：
 * - 有声（≥ FLOOR）按 ≥[MIN_INTERVAL_MS] 采样（约 10Hz）——够重建包络；
 * - 说话首帧立刻记录，不等节流窗；
 * - 从有声跌回静音补记一条 0（收口），之后静音不刷。
 * 真机判据：`Live2dTouch: mouth v=...` 的值-时间对应严丝合缝贴住素材包络
 * （envclip.wav = 1s 静默 →(0.4s 开/0.2s 合)×5→ 1s 静默）。
 */
internal object Live2dMouthTrace {

    /** 低于此电平视为静音，不记录（与 DhTestAudio 的 MOUTH_FLOOR 同值，证据语义一致）。 */
    const val FLOOR = 0.02f

    /** 采样上限 ≈10Hz：够描述包络，不刷屏。 */
    const val MIN_INTERVAL_MS = 100L

    /**
     * 该不该记这条？（[lastLogMs] 传 -1 表示还没有过任何记录。）
     * 纯状态判定，不持时钟——调用方给 nowMs，测试可完全确定。
     */
    fun shouldLog(nowMs: Long, lastLogMs: Long, openness: Float, wasOpen: Boolean): Boolean = when {
        openness >= FLOOR -> lastLogMs < 0 || nowMs - lastLogMs >= MIN_INTERVAL_MS
        wasOpen -> true // 说完收口：补一条 0
        else -> false
    }

    /** 记录后的新状态：(lastLogMs, wasOpen)。 */
    fun advanced(nowMs: Long, openness: Float): Pair<Long, Boolean> = nowMs to openness >= FLOOR
}
