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
        // Live2D / Tao 仍是 SDK 缺席的占位；Duix 已内置运行时，素材没下好时回「未下载」。
        for (id in listOf(DhEngineId.LIVE2D, DhEngineId.TAO_AVATAR)) {
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
    fun duix_without_cache_is_unavailable_with_download_reason_and_menu_still_switches() {
        val row = DhEngineRegistry.available(context).first { it.id == DhEngineId.DUIX }
        assertFalse(row.available)
        assertEquals("未下载", row.reason)
        assertTrue("未下载 must route through the download gate", row.needsDownload)

        val menuRow = dhEngineMenuRows(listOf(row)).first { it.id == DhEngineId.DUIX }
        assertTrue(menuRow.switches)
        val gate = DhEngineDownloadGate(isReady = { false }, ensure = { _, _ -> Result.success(Unit) })
        assertEquals(EngineClickAction.NOT_READY_START_DOWNLOAD, gate.actionFor(menuRow))
    }

    @Test
    fun create_duix_without_cache_still_constructs_and_has_no_view() {
        val engine = DhEngineRegistry.create(context, DhEngineId.DUIX)
        assertEquals(DhEngineId.DUIX, engine.id)
        assertFalse(engine.availability(context).available)
        assertEquals(null, engine.createView(context))
        engine.release()
    }
}
