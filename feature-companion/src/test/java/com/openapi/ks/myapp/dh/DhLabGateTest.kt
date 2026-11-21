package com.openapi.ks.myapp.dh

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DhLabGateTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(DhLabPreferences.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun gate_true_when_lab_forced() {
        // Robolectric apps are typically debuggable; clear that path by relying on forced pref.
        DhLabPreferences(context).labForced = true
        assertTrue(DhLabGate.isEnabled(context))
    }

    @Test
    fun gate_false_when_neither_forced_nor_debuggable() {
        DhLabPreferences(context).labForced = false
        // Strip FLAG_DEBUGGABLE for this assertion.
        val info = context.applicationInfo
        val original = info.flags
        info.flags = original and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        try {
            assertFalse(DhLabGate.isEnabled(context))
        } finally {
            info.flags = original
        }
    }
}
