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
    fun vrm_without_cache_is_unavailable_with_download_reason_and_menu_still_switches() {
        val row = DhEngineRegistry.available(context).first { it.id == DhEngineId.VRM }
        assertFalse(row.available)
        assertEquals("未下载", row.reason)
        assertTrue("未下载 must route through the download gate", row.needsDownload)

        val menuRow = dhEngineMenuRows(listOf(row)).first { it.id == DhEngineId.VRM }
        assertTrue(menuRow.switches)
        val gate = DhEngineDownloadGate(availability = { false }, ensure = { _, _ -> Result.success(Unit) })
        assertEquals(EngineClickAction.NOT_READY_START_DOWNLOAD, gate.actionFor(menuRow))
    }

    @Test
    fun bootstrap_registers_all_four_engines() {
        val avail = DhEngineRegistry.available(context)
        assertEquals(DhEngineId.entries.size, avail.size)
        assertTrue(avail.first { it.id == DhEngineId.SIMPLE }.available)
        // Tao 内置了 NNR 运行时，但 Robolectric 机型（RAM/ABI）或 cache 素材不足时仍不可用；
        // Duix / Live2D 已内置运行时，素材没下好时回「未下载」。
        val tao = avail.first { it.id == DhEngineId.TAO_AVATAR }
        assertFalse("without assets/device gate pass, TAO should be unavailable", tao.available)
        assertTrue(
            "reason for TAO: ${tao.reason}",
            tao.reason?.contains("机型不足") == true ||
                tao.reason == "未下载" ||
                tao.reason == "native 库缺失",
        )
    }

    @Test
    fun tao_menu_row_follows_gate_before_download() {
        val row = DhEngineRegistry.available(context).first { it.id == DhEngineId.TAO_AVATAR }
        val menuRow = dhEngineMenuRows(listOf(row)).first { it.id == DhEngineId.TAO_AVATAR }
        if (row.reason?.contains("机型不足") == true) {
            // Robolectric device is below the 8GB gate: unswitchable, download never offered.
            assertFalse("device-blocked TAO must not be switchable", menuRow.switches)
            assertFalse(row.needsDownload)
            val gate = DhEngineDownloadGate(availability = { false }, ensure = { _, _ -> Result.success(Unit) })
            assertEquals(EngineClickAction.UNAVAILABLE_REASON, gate.actionFor(menuRow))
        } else {
            // Device passed: cold cache is a download, same as Duix / Live2D.
            assertEquals("未下载", row.reason)
            assertTrue(row.needsDownload)
            assertTrue(menuRow.switches)
            val gate = DhEngineDownloadGate(availability = { false }, ensure = { _, _ -> Result.success(Unit) })
            assertEquals(EngineClickAction.NOT_READY_START_DOWNLOAD, gate.actionFor(menuRow))
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
        val gate = DhEngineDownloadGate(availability = { false }, ensure = { _, _ -> Result.success(Unit) })
        assertEquals(EngineClickAction.NOT_READY_START_DOWNLOAD, gate.actionFor(menuRow))
    }

    @Test
    fun live2d_without_cache_is_unavailable_with_download_reason_and_menu_still_switches() {
        val row = DhEngineRegistry.available(context).first { it.id == DhEngineId.LIVE2D }
        assertFalse(row.available)
        assertEquals("未下载", row.reason)
        assertTrue("未下载 must route through the download gate", row.needsDownload)

        val menuRow = dhEngineMenuRows(listOf(row)).first { it.id == DhEngineId.LIVE2D }
        assertTrue(menuRow.switches)
        val gate = DhEngineDownloadGate(availability = { false }, ensure = { _, _ -> Result.success(Unit) })
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

    @Test
    fun tao_does_not_advertise_mouth_while_dispatch_is_a_noop() {
        // Final review I10: the bridge sends Mouth commands to every engine; an engine that
        // silently drops them must not advertise MOUTH, or callers trust a capability that
        // does nothing. A2BS wiring is a native follow-up — until then, STATE only.
        val engine = DhEngineRegistry.create(context, DhEngineId.TAO_AVATAR)
        assertFalse(
            "Tao dispatch() ignores Mouth, so MOUTH must not be advertised",
            engine.capabilities.contains(com.openapi.ks.myapp.avatar.AvatarCapability.MOUTH),
        )
        engine.release()
    }
}
