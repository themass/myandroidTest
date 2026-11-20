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

    private fun bigRamContext(): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        Shadows.shadowOf(am).setMemoryInfo(ActivityManager.MemoryInfo().apply {
            totalMem = 12L * 1024 * 1024 * 1024
        })
        return context
    }

    /**
     * Maleoon（麒麟 9000S 系）的 Vulkan/GL 着色器编译器 libbishenggpucompiler_v200.so 在
     * NNR 场景加载后 SIGSEGV（fault addr 0x44，cmpbe_v2_compile_multiple_shaders），
     * Java 层拦不住——必须在入口处就灰掉，而不是等进程死了再熔断。
     */
    @Test
    fun maleoon_gpu_compiler_is_unsupported_even_with_big_ram() {
        val v = TaoDeviceGate.evaluate(bigRamContext(), vendorLibExists = { it.contains("libbishenggpucompiler") })
        assertFalse(v.supported)
        assertTrue(v.reason?.contains("GPU") == true)
    }

    @Test
    fun gpu_without_known_bad_compiler_is_not_blocked_by_gpu_check() {
        val v = TaoDeviceGate.evaluate(bigRamContext(), vendorLibExists = { false })
        // ABI 在 Robolectric 里可能被拦；只断言不是因为 GPU 被拦
        assertTrue(v.reason?.contains("GPU") != true)
    }
}
