package com.openapi.feature.chat.presentation.ui

import android.app.Activity
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * 钉住「在地图打开」的跳转契约（P2-13，验收表 L4 的 JVM 侧底）。
 *
 * 为什么值得钉：`geo:` URI 是我们手拼的，地图应用（Maps/高德/百度）只认这个形状。
 * 这条线以前一个测试都没有——谁顺手加个 `?z=`（注释里写明了不加：各家支持不一致）、
 * 或者把坐标改成 `geo:0,0?q=`，单测不会响，坏在真机上才暴露。
 *
 * 用 `Robolectric.buildActivity` 而不是 Application context：对话页里 `LocalContext.current`
 * 就是 Activity，`startActivity` 从 Application context 发会因缺 `FLAG_ACTIVITY_NEW_TASK`
 * 抛 `AndroidRuntimeException`（不是 `ActivityNotFoundException`，兜底的 Toast 接不住）。
 * 测试环境和生产一致，才不会把「其实需要 NEW_TASK」这个真问题测成绿的。
 *
 * 设备侧要验的是「有没有地图应用接住它」，这里验的是「我们递出去的形状对不对」——
 * 两层各钉各的，别混。
 */
@RunWith(RobolectricTestRunner::class)
class ChatPlatformMapIntentTest {

    private fun openMapThenIntent(latitude: Double, longitude: Double): Intent {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        ChatPlatform.openMap(activity, latitude, longitude)
        return shadowOf(activity).nextStartedActivity
            ?: error("openMap 没有发出任何 intent")
    }

    @Test
    fun geoIntentCarriesBareCoordinatesNoQuery() {
        val intent = openMapThenIntent(31.2304, 121.4737)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("geo:31.2304,121.4737", intent.dataString)
        // 缩放参数不加是刻意的（各家地图支持不一致）；钉住「无 query」防回归
        assertNull(intent.data?.query)
    }

    @Test
    fun southernHemisphereCoordinatesKeepTheirSign() {
        assertEquals("geo:-33.8688,151.2093", openMapThenIntent(-33.8688, 151.2093).dataString)
    }
}
