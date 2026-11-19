package com.openapi.ks.dh

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Plain JVM tests for [TaoRuntime] availability / createView gating.
 * Uses the injectable seam so we never need Android Context or Robolectric.
 * Device-gate coverage lives in companion ([EngineAdaptersAvailabilityTest]).
 */
class TaoRuntimeAvailabilityTest {

    private class FakeActivity : Activity()

    private class Seen(val host: Activity)

    @Test
    fun createView_null_when_models_missing() {
        val runtime = TaoRuntime.forTest(modelsReady = false)
        assertNull(runtime.createView(FakeActivity()))
        val status = runtime.availability()
        assertFalse(status.available)
        assertEquals("未下载", status.reason)
    }

    @Test
    fun availability_reports_native_missing_when_models_ready() {
        val runtime = TaoRuntime.forTest(modelsReady = true, nativeLoadable = false)
        val status = runtime.availability()
        assertFalse(status.available)
        assertEquals("native 库缺失", status.reason)
        assertNull(runtime.createView(FakeActivity()))
    }

    @Test
    fun createView_builds_tao_view_from_the_supplied_host() {
        val seen = mutableListOf<Seen>()
        val activity = FakeActivity()
        val runtime = TaoRuntime.forTest(
            modelsReady = true,
            nativeLoadable = true,
            cacheRoot = File("."),
            viewBuilder = TaoRuntime.ViewBuilder { host ->
                seen += Seen(host as Activity)
                null
            },
        )
        runtime.createView(activity)
        assertEquals(1, seen.size)
        assertSame("Tao view must be built from the createView host", activity, seen.single().host)
    }

    @Test
    fun package_is_ours() {
        // CameraControlData has no System.loadLibrary, so it loads on plain JVM.
        val fqn = com.openapi.ks.dh.tao.camera.CameraControlData::class.java.name
        assertTrue(
            "vendored FQN must live under com.openapi.ks.dh.tao, got $fqn",
            fqn.startsWith("com.openapi.ks.dh.tao"),
        )
    }
}
