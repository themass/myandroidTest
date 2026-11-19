package com.openapi.ks.dh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Plain JVM tests for [Live2dRuntime] availability / createView gating, plus a
 * repo-path check that the approved Cubism Core arm64 `.so` is in jniLibs.
 */
class Live2dRuntimeAvailabilityTest {

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
        assertNull(runtime.createView())
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
        assertNull(runtime.createView())
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
}
