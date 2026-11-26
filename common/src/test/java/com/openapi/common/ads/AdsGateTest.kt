package com.openapi.common.ads

import com.openapi.common.ads.base.AdsGate
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 广告总开关的行为钉子。
 *
 * 历史：`28b2226e`（2026-11-21，P2-13）曾把 debug 构建整体关广告（冷启动弹
 * 全屏广告页干扰验收）。用户 20261125 亲令「一直开，忽略debug」——决定记录在
 * 台账 docs/design/20261125-admob-ads-ledger.md §6 D1。剧本验收的干扰由
 * `script_case`（启动页跳过开屏 + InterstitialCadence.scriptQuiet 静音插屏）
 * 承接，不再由构建类型整体关闸。
 *
 * 这条测试钉的是「恒开」这个决定本身：将来任何人想把闸改回按构建类型关，
 * 必须过这条测试（和台账里用户亲令的记录）。
 */
class AdsGateTest {

    @Test
    fun `ads are always enabled, debug build ignored (user decision 20261125)`() {
        assertTrue(AdsGate.enabled())
        // debugBuild 标记仍在（别处日志/诊断用），但闸门不读它。
        AdsGate.debugBuild = true
        assertTrue(AdsGate.enabled())
        AdsGate.debugBuild = false
    }
}
