package com.openapi.common.ads.base

/**
 * 广告展示的纯判定逻辑：不读 Android 状态、不碰 SDK，输入全在参数里，JVM 直接测
 * （`common/src/test/.../AdsShowPolicyTest.kt`）。
 *
 * 时机规则移植自 `4ff9d4e5 广告时机闸门…`（`AdGate.java`）。那个 commit 只在
 * `origin/global-vpn` 上，**不在主干系谱**（`git merge-base --is-ancestor` → NO），
 * 所以主干从来没有插屏时机闸门；恢复插屏必须补上，否则会复现它 commit message
 * 里点名的 Play 拒审风险（插屏打断核心操作 / 连续弹）。
 */
object AdsShowPolicy {

    /**
     * 启动静默期：进程启动后这段时间内不弹任何插屏（AdMob 政策禁止「打开应用立即插屏」）。
     * 取 5s：启动页倒计时 `STARTUP_6000+1000` ≈7s 后进主页，正好放行旧版「进主页弹插屏」
     * 的行为（AD2 恢复），又挡住字面意义上的打开即弹。
     */
    const val STARTUP_QUIET_MS = 5_000L

    /** 两次插屏的最小间隔，避免连续切页连弹。 */
    const val MIN_GAP_MS = 45_000L

    /**
     * 概率命中。旧口径 `Md5.getRandom(AdsIds.maxRate) <= AdsIds.PROBABILITY`
     * = `nextInt(10) <= 3` → 4/10。参数化后（draw 由调用方给）可测。
     */
    @JvmStatic
    @JvmOverloads
    fun hitProbability(draw: Int, probability: Int = 3): Boolean = draw <= probability

    /** 插屏时机：过了启动静默期，且距上次插屏不小于最小间隔。 */
    @JvmStatic
    @JvmOverloads
    fun allowInterstitial(
        nowMs: Long,
        startupAtMs: Long,
        lastShownAtMs: Long,
        startupQuietMs: Long = STARTUP_QUIET_MS,
        minGapMs: Long = MIN_GAP_MS,
    ): Boolean {
        if (nowMs - startupAtMs < startupQuietMs) return false
        if (lastShownAtMs > 0L && nowMs - lastShownAtMs < minGapMs) return false
        return true
    }

    /** 列表内 banner 槽位（沿用 `Constants.BANNER_ADS_POS` 的位置表口径）。 */
    @JvmStatic
    fun bannerSlotAt(position: Int, positions: List<Int>): Boolean = positions.contains(position)

    /**
     * banner 类别（映射到 `AdsIds.adviewToAdmobBanner` 的广告位）。
     * 口径抄自现存活代码 `TextChannelListItemsViewAdapter.java:80-88`：
     * `position % 3 == 0 → VPN3`，`== 1 → VPN2`，其余 `VPN1`。
     */
    @JvmStatic
    fun bannerCategreyAt(position: Int): AdsContext.Categrey = when (position % 3) {
        0 -> AdsContext.Categrey.CATEGREY_VPN3
        1 -> AdsContext.Categrey.CATEGREY_VPN2
        else -> AdsContext.Categrey.CATEGREY_VPN1
    }

    /**
     * 「每第 N 条一个 banner，整表最多 cap 个」——
     * AD4 chat 列表恢复用的规则（旧实现是 per-holder `counter.get() < 2`，
     * 见台账 §5）。返回 0-based 下标，调用方据此在 LazyColumn 里插 item。
     */
    @JvmStatic
    fun bannerPositionsEvery(every: Int, listSize: Int, cap: Int): List<Int> {
        if (every <= 0 || cap <= 0) return emptyList()
        val out = ArrayList<Int>()
        var i = every - 1
        while (i < listSize && out.size < cap) {
            out.add(i)
            i += every
        }
        return out
    }
}
