package com.openapi.feature.chat.presentation.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * 钉住「在地图打开」的跳转契约（P2-13 + 2026-11-28 直跳整改）。
 *
 * 契约两条：
 *  1. 第一发必须**点名高德**（`setPackage`）——真机上裸 geo: 会弹系统 chooser，
 *     用户判定为「没接上高德」；装了高德就必须直达。
 *  2. 高德接不上（没装 / 版本不认）退回**裸 geo:**（Maps/百度认这个形状），
 *     坐标手拼、不拼 `?z=`（各家支持不一致），query 必须为空。
 *
 * 用注入的 startActivity 而不是 shadowOf(activity).nextStartedActivity：
 * 要把「第一发 ActivityNotFoundException、第二发成功」这个真实失败形状喂进去，
 * shadow 记录器没法模拟「系统拒绝某个 intent」。坐标契约在两条路上都钉。
 */
@RunWith(RobolectricTestRunner::class)
class ChatPlatformMapIntentTest {

    private class Recorder {
        val sent = mutableListOf<Intent>()
        /** 前 n 发抛 ActivityNotFoundException（其余正常记录）。两发都先进 sent——
         *  「第一发点名高德、第二发裸 geo:」的契约要靠两发都可见。 */
        var failures = 0
        operator fun invoke(intent: Intent) {
            sent += intent
            if (failures > 0) {
                failures--
                throw ActivityNotFoundException()
            }
        }
    }

    private fun open(lat: Double, lon: Double, failures: Int = 0): Recorder {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val rec = Recorder().apply { this.failures = failures }
        ChatPlatform.openMap(activity, lat, lon, startActivity = rec::invoke)
        return rec
    }

    @Test
    fun firstShotIsNamedAtAmapWithBareCoordinates() {
        val rec = open(31.2304, 121.4737)
        assertEquals(1, rec.sent.size)
        val intent = rec.sent.single()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("geo:31.2304,121.4737", intent.dataString)
        assertEquals("必须点名直跳高德，不弹 chooser", "com.autonavi.minimap", intent.`package`)
        // 缩放参数不加是刻意的（各家地图支持不一致）；钉住「无 query」防回归
        assertNull(intent.data?.query)
    }

    @Test
    fun amapMissingFallsBackToBareGeoChooser() {
        val rec = open(31.2304, 121.4737, failures = 1)
        assertEquals("高德接不上要有第二发", 2, rec.sent.size)
        val fallback = rec.sent[1]
        assertEquals(Intent.ACTION_VIEW, fallback.action)
        assertEquals("geo:31.2304,121.4737", fallback.dataString)
        assertNull("回退必须是裸 geo:（不点名任何包）", fallback.`package`)
    }

    @Test
    fun bothFailuresDoNotCrashTheChat() {
        // 一个地图应用都没有：ActivityNotFoundException 不能抛穿到对话页（实现里
        // Toast 兜底）。Robolectric 下库模块的 string 资源没合并进测试 App
        // （Resources.NotFoundException），那是测试环境假象——资源在 packaged_res 里，
        // 这里只钉「不抛 ActivityNotFoundException」。
        val failure = runCatching { open(31.2304, 121.4737, failures = 2).sent.size }
            .exceptionOrNull()
        assertTrue(
            "跳转失败不能把 ActivityNotFoundException 抛给 UI: $failure",
            failure == null || failure !is ActivityNotFoundException,
        )
    }

    @Test
    fun southernHemisphereCoordinatesKeepTheirSign() {
        assertEquals("geo:-33.8688,151.2093", open(-33.8688, 151.2093).sent.single().dataString)
    }
}
