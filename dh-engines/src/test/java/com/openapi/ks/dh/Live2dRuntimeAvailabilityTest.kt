package com.openapi.ks.dh

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Plain JVM tests for [Live2dRuntime] availability / createView gating, plus a
 * repo-path check that the approved Cubism Core arm64 `.so` is in jniLibs.
 *
 * Activity-context coverage: [Live2dRuntime.forTest] injects a [Live2dRuntime.ViewBuilder] that
 * records the host Context and resolved Activity which [Live2dRuntime.createView] hands to the GL
 * path (GLSurfaceView construction and `JniBridgeJava.SetActivityInstance`). Plain JVM, no
 * Robolectric: [FakeActivity] only needs to be constructible because `findActivity()` checks
 * `is Activity` before calling any framework method.
 */
class Live2dRuntimeAvailabilityTest {

    private class FakeActivity : Activity()

    /** Mirrors Compose / theme wrappers around an Activity (getBaseContext chain). */
    private class WrapperOver(private val inner: Context) : ContextWrapper(null) {
        override fun getBaseContext(): Context = inner
    }

    private class Seen(val host: Context, val activity: Activity?)

    private fun recordingRuntime(seen: MutableList<Seen>): Live2dRuntime = Live2dRuntime.forTest(
        modelsReady = true,
        nativeLoadable = true,
        cacheRoot = File("."),
        viewBuilder = Live2dRuntime.ViewBuilder { host, activity ->
            seen += Seen(host, activity)
            null
        },
    )

    @Test
    fun cubism_core_arm64_so_is_in_jniLibs() {
        val candidates = listOf(
            File("src/main/jniLibs/arm64-v8a/libLive2DCubismCore.so"),
            File("dh-engines/src/main/jniLibs/arm64-v8a/libLive2DCubismCore.so"),
        )
        val so = candidates.firstOrNull { it.isFile }
        assertNotNull(
            "Cubism Core .so missing; looked in $candidates (cwd=${File(".").absolutePath})",
            so,
        )
        // ChatWaifu ships ~385KB; brief's ~1.6MB was an upper-bound estimate.
        assertTrue("unexpectedly tiny: ${so!!.length()} bytes", so.length() > 100_000L)
    }

    @Test
    fun createView_null_when_models_missing() {
        val runtime = Live2dRuntime.forTest(modelsReady = false)
        assertNull(runtime.createView(FakeActivity()))
        val status = runtime.availability()
        assertFalse(status.available)
        assertEquals("未下载", status.reason)
    }

    @Test
    fun availability_reports_native_missing_when_models_ready() {
        val runtime = Live2dRuntime.forTest(modelsReady = true, nativeLoadable = false)
        val status = runtime.availability()
        assertFalse(status.available)
        assertEquals("native 库缺失", status.reason)
        assertNull(runtime.createView(FakeActivity()))
    }

    @Test
    fun createView_builds_gl_from_the_supplied_activity_host() {
        val seen = mutableListOf<Seen>()
        val activity = FakeActivity()
        recordingRuntime(seen).createView(activity)
        assertEquals(1, seen.size)
        assertSame("GL view must be built from the createView host", activity, seen.single().host)
        assertSame("Activity must reach SetActivityInstance", activity, seen.single().activity)
    }

    @Test
    fun createView_resolves_activity_through_context_wrappers() {
        val seen = mutableListOf<Seen>()
        val activity = FakeActivity()
        val wrapper = WrapperOver(activity)
        recordingRuntime(seen).createView(wrapper)
        assertEquals(1, seen.size)
        assertSame(wrapper, seen.single().host)
        assertSame("findActivity must unwrap to the Activity", activity, seen.single().activity)
    }

    @Test
    fun package_is_ours() {
        // GLRenderer has no static System.loadLibrary, so it loads on plain JVM.
        val fqn = com.openapi.ks.dh.live2d.GLRenderer::class.java.name
        assertTrue(
            "vendored FQN must live under com.openapi.ks.dh.live2d, got $fqn",
            fqn.startsWith("com.openapi.ks.dh.live2d"),
        )
    }

    @Test
    fun release_pauses_the_gl_thread_before_native_teardown() {
        // 设备 SIGSEGV（2026-11-20 00:10:31，选 Tao 切引擎瞬间）：主线程 release() 直接
        // nativeOnDestroy 释放 native 场景，而 GLThread 还在 guardedRun 里处理 egl
        // createSurface/onSurfaceChanged → libchatwaifu-live2d.so 里 null 解引用。
        // GLSurfaceView.onPause() 会同步等 GLThread 退出渲染循环，必须先于任何 native 拆除。
        var dir = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "settings.gradle").isFile) dir = dir.parentFile
        val src = File(checkNotNull(dir), "dh-engines/src/main/java/com/openapi/ks/dh/Live2dRuntime.kt").readText()
        val rel = src.substringAfter("fun release()")
        val pause = rel.indexOf("onPause()")
        assertTrue("release() must call GLSurfaceView.onPause() (joins the GLThread)", pause >= 0)
        assertTrue("nativeOnStop must run after onPause (GLThread already stopped)", rel.indexOf("nativeOnStop()") > pause)
        assertTrue("nativeOnDestroy must run after onPause", rel.indexOf("nativeOnDestroy()") > pause)
    }
}
