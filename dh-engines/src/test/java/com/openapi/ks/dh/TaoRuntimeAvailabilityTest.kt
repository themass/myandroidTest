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
        var seenHost: android.content.Context? = null
        var seenDir: String? = null
        val activity = FakeActivity()
        val runtime = TaoRuntime.forTest(
            modelsReady = true,
            nativeLoadable = true,
            cacheRoot = File("."),
            viewKit = TaoRuntime.ViewKit { host, modelDir ->
                seenHost = host
                seenDir = modelDir
                TaoRuntime.Built(null)
            },
        )
        assertNull(runtime.createView(activity))
        assertSame("Tao view must be built from the createView host", activity, seenHost)
        assertTrue(
            "kit must receive the NNR model dir derived from the cache root, got $seenDir",
            seenDir?.endsWith("TaoAvatar-NNR-MNN/") == true,
        )
    }

    @Test
    fun createView_starts_the_render_boot() {
        // Upstream booted NNR from MainActivity.loadNNRModel() -> waitForInitComplete(); the
        // facade must kick it, or the TextureView shows the placeholder forever (the native NNR
        // instance is never created — Task-6 review finding 1).
        var booted = 0
        val runtime = TaoRuntime.forTest(
            modelsReady = true,
            nativeLoadable = true,
            cacheRoot = File("."),
            viewKit = TaoRuntime.ViewKit { _, _ ->
                TaoRuntime.Built(null) { booted++ }
            },
        )
        runtime.createView(FakeActivity())
        assertEquals("render boot must start so the NNR instance gets created", 1, booted)
    }

    @Test
    fun render_and_camera_classes_keep_the_upstream_fqn() {
        // Inverted since the local libtaoavatar.so build: the render/camera classes are the
        // JNI surface (symbols + hardcoded field descriptor), so they live at
        // com.taobao.meta.avatar.* — TaoNativeBridgeTest pins the full contract. Only the
        // orchestration (MHConfig, A2BS stub, runtime facade) stays in our package.
        val fqn = com.taobao.meta.avatar.camera.CameraControlData::class.java.name
        assertEquals("com.taobao.meta.avatar.camera.CameraControlData", fqn)
    }
}
