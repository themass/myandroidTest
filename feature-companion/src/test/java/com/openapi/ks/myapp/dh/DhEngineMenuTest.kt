package com.openapi.ks.myapp.dh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DhEngineMenuTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(DhLabPreferences.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun callStage_defaults_to_original_even_when_engine_is_simple() {
        val prefs = DhLabPreferences(context)
        assertEquals(DhEngineId.SIMPLE, prefs.engine)
        assertNull(prefs.callStage)
    }

    @Test
    fun callStage_persists_choice_and_clears_back_to_original() {
        val prefs = DhLabPreferences(context)
        prefs.callStage = DhEngineId.DUIX
        assertEquals(DhEngineId.DUIX, DhLabPreferences(context).callStage)
        DhLabPreferences(context).callStage = null
        assertNull(DhLabPreferences(context).callStage)
    }

    @Test
    fun menu_starts_with_original_and_names_why_an_engine_cannot_switch() {
        val rows = dhEngineMenuRows(
            listOf(
                DhEngineAvailability(DhEngineId.SIMPLE, available = true),
                DhEngineAvailability(DhEngineId.LIVE2D, available = true),
                DhEngineAvailability(DhEngineId.DUIX, available = false, reason = "缺模型"),
                DhEngineAvailability(DhEngineId.TAO_AVATAR, available = false, reason = "未编入"),
            ),
        )
        assertEquals(null, rows.first().id)
        assertEquals("原来的形象", rows.first().label)
        assertTrue(rows.first().switches)
        assertEquals("✓ 原来的形象", rows.first().menuLabel(selected = true))
        val duix = rows.first { it.id == DhEngineId.DUIX }
        assertFalse(duix.switches)
        assertEquals("　 Duix（缺模型）", duix.menuLabel(selected = false))
        assertEquals(5, rows.size)
    }

    @Test
    fun vrm_menu_row_shows_experiment_label() {
        val rows = dhEngineMenuRows(
            listOf(DhEngineAvailability(DhEngineId.VRM, available = false, reason = "未下载", needsDownload = true)),
        )
        val vrm = rows.first { it.id == DhEngineId.VRM }
        assertEquals("VRM (实验)", vrm.label)
        assertTrue(vrm.switches)
        assertEquals("未下载", vrm.detail)
    }

    @Test
    fun menu_keeps_download_pending_engine_switchable_but_device_blocked_one_not() {
        val rows = dhEngineMenuRows(
            listOf(
                DhEngineAvailability(DhEngineId.DUIX, available = false, reason = "未下载", needsDownload = true),
                DhEngineAvailability(DhEngineId.TAO_AVATAR, available = false, reason = "机型不足"),
            ),
        )
        val duix = rows.first { it.id == DhEngineId.DUIX }
        assertTrue(duix.switches)
        assertEquals("未下载", duix.detail)
        val tao = rows.first { it.id == DhEngineId.TAO_AVATAR }
        assertFalse(tao.switches)
        assertEquals("机型不足", tao.detail)
    }
}
