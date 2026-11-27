package com.openapi.common.ads.base

/**
 * 插屏节律（进程级）：启动静默期 + 两次插屏最小间隔。
 *
 * 规则移植自 `4ff9d4e5 广告时机闸门…`（仅在 origin/global-vpn 上的 `AdGate.java`），
 * 主干从来没有这道闸——恢复插屏必须带上，否则「进页面就弹全屏」会被 Play 按
 * 「广告干扰核心功能」处理。判定本体在 [AdsShowPolicy.allowInterstitial]（纯函数），
 * 本对象只持有时间戳，方便 JVM 注入时钟测试（AdsShowPolicyTest）。
 */
object InterstitialCadence {

    /** 可注入时钟：测试推进时间验证静默期/间隔。 */
    @Volatile
    @JvmStatic
    var clock: AdsClock = AdsClock { System.currentTimeMillis() }

    /**
     * adb 剧本验收（`script_case`）期间静音插屏：全屏广告页会打断脚本
     * （P2-13 同源干扰）。闸总开（用户 20261125 亲令），剧本保护放这一层。
     */
    @Volatile
    @JvmStatic
    var scriptQuiet: Boolean = false

    private var startupAtMs = 0L
    private var lastShownAtMs = 0L

    /**
     * 进程启动锚点（MyApplication.onCreate 调一次）。同时清空上一轮节律状态：
     * 进程启动时必然没有「本进程已展示」的插屏；也让 JVM 测试可重复调用此方法隔离状态。
     */
    @JvmStatic
    @Synchronized
    fun noteStartup() {
        startupAtMs = clock.now()
        lastShownAtMs = 0L
    }

    /** 真正展示过一屏插屏之后调用（决定下一次的最小间隔）。 */
    @JvmStatic
    fun noteShown() {
        lastShownAtMs = clock.now()
    }

    /** 当前时刻是否允许弹插屏。 */
    @JvmStatic
    fun allow(): Boolean =
        !scriptQuiet && AdsShowPolicy.allowInterstitial(clock.now(), startupAtMs, lastShownAtMs)
}
