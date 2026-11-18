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
class DhLabPreferencesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(DhLabPreferences.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun prefs_default_simple() {
        val prefs = DhLabPreferences(context)
        assertEquals(DhEngineId.SIMPLE, prefs.engine)
        assertFalse(prefs.labForced)
    }

    @Test
    fun prefs_persist_engine() {
        val prefs = DhLabPreferences(context)
        prefs.engine = DhEngineId.DUIX
        assertEquals(DhEngineId.DUIX, DhLabPreferences(context).engine)
    }

    @Test
    fun prefs_lab_forced() {
        val prefs = DhLabPreferences(context)
        prefs.labForced = true
        assertTrue(DhLabPreferences(context).labForced)
    }
}
