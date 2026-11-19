package com.openapi.ks.dh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain JVM tests for [DuixRuntime] availability / createView gating.
 * Uses the injectable seam so we never need Android Context or Robolectric.
 */
class DuixRuntimeAvailabilityTest {

    @Test
    fun createView_null_when_models_missing() {
        val runtime = DuixRuntime.forTest(modelsReady = false)
        assertNull(runtime.createView())
        val status = runtime.availability()
        assertFalse(status.available)
        assertEquals("未下载", status.reason)
    }

    @Test
    fun package_is_ours() {
        // Hand-authored BuildConfig has no Android deps, so it loads on plain JVM.
        val fqn = com.openapi.ks.dh.duix.sdk.client.BuildConfig::class.java.name
        assertTrue(
            "vendored FQN must live under com.openapi.ks.dh.duix, got $fqn",
            fqn.startsWith("com.openapi.ks.dh.duix"),
        )
    }
}
