package com.openapi.common.ads

import com.openapi.common.ads.base.AdsClock
import com.openapi.common.ads.base.AdsContext
import com.openapi.common.ads.base.AdsGate
import com.openapi.common.ads.base.AdsShowPolicy
import com.openapi.common.ads.base.InterstitialCadence
import com.openapi.common.ads.base.RateBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 广告展示门控的纯逻辑（台账 docs/design/20261125-admob-ads-ledger.md §0/§7）。
 *
 * 钉三件事：
 * 1. debug 闸的行为不能退化（`28b2226e` 是为了真机验收不被全屏广告干扰），
 *    但必须留一条显式 opt-in，否则装机验收永远看不到广告，恢复无从验证。
 * 2. `AdsContext.rateShow()` 的 `showCount++ > 8` 是全仓无复位的 static，
 *    第 9 次起整个进程所有插屏永久 false——这是「广告不见了」里唯一 release 也中招的缺陷。
 *    修成窗口化预算：窗口内仍然限流，窗口过期后重新放行。
 * 3. 插屏时机闸门（`4ff9d4e5` 的 AdGate）只存在于 origin/global-vpn，主干从来没有；
 *    恢复插屏必须带上启动静默期与最小间隔，否则复现该 commit 描述的拒审风险。
 */
class AdsShowPolicyTest {

    // ---- 1) 构建闸 ----

    @Test
    fun `debug build without opt-in shows no ads`() {
        assertFalse(AdsGate.enabled(isDebugBuild = true, optIn = false))
    }

    @Test
    fun `debug build with explicit opt-in shows ads`() {
        assertTrue(AdsGate.enabled(isDebugBuild = true, optIn = true))
    }

    @Test
    fun `release build needs no opt-in`() {
        assertTrue(AdsGate.enabled(isDebugBuild = false, optIn = false))
    }

    // ---- 2) 概率 + 窗口预算 ----

    @Test
    fun `budget throttles to 8 requests per window`() {
        var t = 0L
        val budget = RateBudget(clock = AdsClock { t }, budget = 8, windowMs = 600_000)
        repeat(8) { i ->
            t += 1_000
            assertTrue("窗口内第 ${i + 1} 次应放行", budget.take())
        }
        t += 1_000
        assertFalse("窗口内第 9 次仍应限流", budget.take())
    }

    @Test
    fun `budget reopens when the window expires`() {
        // 旧实现这里永久 false：showCount 只增不复位。这条钉住修复。
        var t = 0L
        val budget = RateBudget(clock = AdsClock { t }, budget = 8, windowMs = 600_000)
        repeat(9) { t += 1_000; budget.take() }
        assertFalse(budget.take())
        t += 600_000
        assertTrue("窗口过期后必须重新放行", budget.take())
    }

    @Test
    fun `probability keeps the historical 40 percent boundary`() {
        // 旧代码 getRandom(10) <= 3 → 0..3 命中，共 4/10。
        assertTrue(AdsShowPolicy.hitProbability(draw = 0, probability = 3))
        assertTrue(AdsShowPolicy.hitProbability(draw = 3, probability = 3))
        assertFalse(AdsShowPolicy.hitProbability(draw = 4, probability = 3))
    }

    // ---- 3) 插屏时机 ----

    @Test
    fun `interstitial blocked inside the startup quiet period`() {
        assertFalse(AdsShowPolicy.allowInterstitial(nowMs = 3_000, startupAtMs = 0, lastShownAtMs = 0))
        // 启动页倒计时 7s 后进主页：静默期（5s）已过，AD2 的「进主页插屏」应放行
        assertTrue(AdsShowPolicy.allowInterstitial(nowMs = 7_000, startupAtMs = 0, lastShownAtMs = 0))
    }

    @Test
    fun `interstitial blocked closer than the minimum gap to the previous one`() {
        assertFalse(
            AdsShowPolicy.allowInterstitial(nowMs = 200_000, startupAtMs = 0, lastShownAtMs = 180_000),
        )
    }

    @Test
    fun `interstitial allowed once both windows passed`() {
        assertTrue(
            AdsShowPolicy.allowInterstitial(nowMs = 300_000, startupAtMs = 0, lastShownAtMs = 100_000),
        )
    }

    @Test
    fun `cadence remembers the moment it showed one`() {
        var t = 100_000L
        InterstitialCadence.clock = AdsClock { t }
        InterstitialCadence.noteStartup()
        assertFalse("刚进首页不该立刻弹插屏", InterstitialCadence.allow())
        t += 21_000
        assertTrue(InterstitialCadence.allow())
        InterstitialCadence.noteShown()
        t += 44_000
        assertFalse("两次插屏间隔不足 45s", InterstitialCadence.allow())
        t += 2_000
        assertTrue(InterstitialCadence.allow())
    }

    // ---- 4) banner 位置规则（AD3 列表位 / AD4 chat 列表位共用） ----

    @Test
    fun `legacy channel banner positions are honoured`() {
        val legacy = listOf(2, 8, 13, 18, 21, 30, 40, 50) // Constants.BANNER_ADS_POS
        assertTrue(AdsShowPolicy.bannerSlotAt(8, legacy))
        assertFalse(AdsShowPolicy.bannerSlotAt(9, legacy))
    }

    @Test
    fun `banner categrey keeps the legacy position modulo three rule`() {
        // 抄 TextChannelListItemsViewAdapter.java:80-88：%3==0→VPN3，%3==1→VPN2，其余 VPN1。
        assertEquals(AdsContext.Categrey.CATEGREY_VPN3, AdsShowPolicy.bannerCategreyAt(3))
        assertEquals(AdsContext.Categrey.CATEGREY_VPN2, AdsShowPolicy.bannerCategreyAt(13))
        assertEquals(AdsContext.Categrey.CATEGREY_VPN1, AdsShowPolicy.bannerCategreyAt(2))
        assertEquals(AdsContext.Categrey.CATEGREY_VPN1, AdsShowPolicy.bannerCategreyAt(8))
    }

    @Test
    fun `chat list caps the number of banners`() {
        // 旧 chat 规则是「每类 holder 最多 2 条」(counter<2)，新实现按「每 N 条 + 上限」。
        assertEquals(listOf(5, 11), AdsShowPolicy.bannerPositionsEvery(every = 6, listSize = 12, cap = 2))
        assertEquals(listOf(5, 11, 17), AdsShowPolicy.bannerPositionsEvery(every = 6, listSize = 20, cap = 3))
        assertEquals(emptyList<Int>(), AdsShowPolicy.bannerPositionsEvery(every = 6, listSize = 5, cap = 3))
    }
}
