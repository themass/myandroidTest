package com.openapi.ks.myapp.dh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EngineAdaptersAvailabilityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DhEngineRegistry.resetForTests()
        DhLabBootstrap.resetForTests()
        DhLabBootstrap.ensureDefaults()
    }

    @Test
    fun bootstrap_registers_all_four_engines() {
        val avail = DhEngineRegistry.available(context)
        assertEquals(DhEngineId.entries.size, avail.size)
        assertTrue(avail.first { it.id == DhEngineId.SIMPLE }.available)
        // 无 SDK classpath 时三家均不可用，但必须已注册（原因不是「未编入」工厂缺失，
        // 而是引擎自身 availability；反射不到类时仍回「未编入」）。
        for (id in listOf(DhEngineId.LIVE2D, DhEngineId.DUIX, DhEngineId.TAO_AVATAR)) {
            val row = avail.first { it.id == id }
            assertFalse("without SDK, $id should be unavailable", row.available)
            assertTrue(
                "reason for $id: ${row.reason}",
                row.reason?.contains("未编入") == true ||
                    row.reason?.contains("缺模型") == true ||
                    row.reason?.contains("机型不足") == true,
            )
        }
    }

    @Test
    fun create_duix_without_sdk_still_constructs_and_reports_unavailable() {
        val engine = DhEngineRegistry.create(context, DhEngineId.DUIX)
        assertEquals(DhEngineId.DUIX, engine.id)
        val a = engine.availability(context)
        assertFalse(a.available)
        engine.release()
    }
}
