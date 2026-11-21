package com.openapi.common.ads

import com.openapi.common.ads.base.AdsGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * debug 构建不加载广告（真机验收踩到：chat debug 包冷启动即弹全屏插屏，
 * 误触点广告跳进 googleadservices 证书报警页）。
 *
 * 测试与开发用的都是 debug 签名，广告 SDK 在这类包里只有负面作用：
 * 干扰验收、点击会打开测试机里配置不全的外部域名。release 行为不变。
 */
class AdsGateTest {

    @Test
    fun `debug builds do not load ads`() {
        assertFalse(AdsGate.enabled(isDebugBuild = true))
    }

    @Test
    fun `release builds keep ads`() {
        assertTrue(AdsGate.enabled(isDebugBuild = false))
    }
}
