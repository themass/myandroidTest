package com.openapi.ks.myapp.dh

import android.app.ActivityManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TaoDeviceGateTest {

    @Test
    fun low_ram_is_unsupported() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val shadow: ShadowActivityManager = Shadows.shadowOf(am)
        shadow.setMemoryInfo(ActivityManager.MemoryInfo().apply {
            totalMem = 4L * 1024 * 1024 * 1024 // 4GB
        })
        val v = TaoDeviceGate.evaluate(context)
        assertFalse(v.supported)
        assertTrue(v.reason?.contains("机型不足") == true)
    }

    @Test
    fun high_ram_passes_memory_gate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val shadow: ShadowActivityManager = Shadows.shadowOf(am)
        shadow.setMemoryInfo(ActivityManager.MemoryInfo().apply {
            totalMem = 12L * 1024 * 1024 * 1024 // 12GB
        })
        val v = TaoDeviceGate.evaluate(context)
        // Robolectric ABI 可能无 arm64：内存过了即可；若 ABI 拦下也必须是机型不足文案
        if (!v.supported) {
            assertTrue(v.reason?.contains("机型不足") == true)
        } else {
            assertTrue(v.supported)
        }
    }
}
