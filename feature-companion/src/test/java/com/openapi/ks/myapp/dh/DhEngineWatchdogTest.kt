package com.openapi.ks.myapp.dh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 进程级形象熔断（用户要求「出问题了就降级到最基础的无形象页面」）。
 *
 * native SIGSEGV 在 Java 层拦不住（本分支修掉的两个都是），所以冷启动时看标记：
 * 上次进程死在某个 native 引擎的形象页（markActive 后没有 markClean），本次就隔离
 * 该引擎——[DhEngineWatchdog.onColdStart] 返回它，LabHost 直接降级 SIMPLE，
 * 直到用户在引擎菜单里主动重新选择才解除（clear）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DhEngineWatchdogTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(DhEngineWatchdog.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun fresh_install_has_no_quarantine() {
        assertNull(DhEngineWatchdog(context).onColdStart())
    }

    @Test
    fun crash_while_active_quarantines_the_engine_on_next_boot() {
        DhEngineWatchdog(context).markActive(DhEngineId.TAO_AVATAR)
        // 新进程（崩溃后重启）：active 标记还在 → 上次死在 Tao 形象页。
        val reboot = DhEngineWatchdog(context)
        assertEquals(DhEngineId.TAO_AVATAR, reboot.onColdStart())
    }

    @Test
    fun clean_dispose_never_quarantines() {
        val w = DhEngineWatchdog(context)
        w.markActive(DhEngineId.DUIX)
        w.markClean()
        assertNull(DhEngineWatchdog(context).onColdStart())
    }

    @Test
    fun quarantine_survives_repeated_boots_until_cleared() {
        DhEngineWatchdog(context).markActive(DhEngineId.LIVE2D)
        assertEquals(DhEngineId.LIVE2D, DhEngineWatchdog(context).onColdStart())
        // 第二次冷启动：active→quarantined 只发生一次（onColdStart 返回 null），
        // 但隔离仍在——LabHost 每次都查 isQuarantined。
        assertNull(DhEngineWatchdog(context).onColdStart())
        assertTrue(DhEngineWatchdog(context).isQuarantined(DhEngineId.LIVE2D))
        DhEngineWatchdog(context).clear(DhEngineId.LIVE2D)
        assertNull(DhEngineWatchdog(context).onColdStart())
    }

    @Test
    fun is_quarantined_reflects_state_for_the_host() {
        DhEngineWatchdog(context).markActive(DhEngineId.TAO_AVATAR)
        val w = DhEngineWatchdog(context)
        w.onColdStart()
        assertTrue(w.isQuarantined(DhEngineId.TAO_AVATAR))
        assertTrue(!w.isQuarantined(DhEngineId.DUIX))
    }

    @Test
    fun quarantine_only_holds_one_engine_and_the_newer_crash_wins() {
        val w = DhEngineWatchdog(context)
        w.markActive(DhEngineId.DUIX)
        w.markClean()
        w.markActive(DhEngineId.TAO_AVATAR)
        assertEquals(DhEngineId.TAO_AVATAR, DhEngineWatchdog(context).onColdStart())
        assertTrue(!DhEngineWatchdog(context).isQuarantined(DhEngineId.DUIX))
    }

    @Test
    fun lab_host_wireup_gates_creation_on_the_watchdog() {
        // 源码守护：LabHost 必须在 remember(engineId)/replaceWith 两处都过熔断，
        // 且激活时 markActive、onDispose 时 markClean——否则标记永远脏或永远净。
        var dir = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "settings.gradle").isFile) dir = dir.parentFile
        val src = File(checkNotNull(dir), "feature-companion/src/main/java/com/openapi/ks/myapp/dh/DigitalHumanLabHost.kt").readText()
        assertTrue("host must consult the watchdog", src.contains("isQuarantined"))
        assertTrue("host must arm the fuse when an engine goes live", src.contains("markActive("))
        assertTrue("host must disarm on dispose", src.contains("markClean()"))
    }
}
