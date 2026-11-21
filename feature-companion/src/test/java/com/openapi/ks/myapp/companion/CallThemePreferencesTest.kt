package com.openapi.ks.myapp.companion

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 背景主题的持久化：与波形样式同套路（SharedPreferences + 名字）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CallThemePreferencesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(CALL_THEME_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `没设置过就是默认主题`() {
        assertEquals(CallTheme.Default.id, loadCallTheme(context).id)
    }

    @Test
    fun `存过就能读回来`() {
        val picked = CallTheme.Presets.first { it.id != CallTheme.Default.id }
        saveCallTheme(context, picked)
        assertEquals(picked.id, loadCallTheme(context).id)
    }

    @Test
    fun `存的值被旧版本写坏时回落默认，不崩`() {
        context.getSharedPreferences(CALL_THEME_PREFS, Context.MODE_PRIVATE).edit()
            .putString("theme", "no_such_theme").commit()
        assertEquals(CallTheme.Default.id, loadCallTheme(context).id)
    }

    @Test
    fun `自定义主题存色相，读回同色相`() {
        saveCallTheme(context, customCallTheme(180f))
        val loaded = loadCallTheme(context)
        assertEquals(CallTheme.CUSTOM_ID, loaded.id)
        assertEquals(180f, loaded.hueDegrees!!)
        assertEquals(customCallTheme(180f).topArgb, loaded.topArgb)
    }

    @Test
    fun `自定义标记在但色相丢了：默认色相顶上，不崩`() {
        context.getSharedPreferences(CALL_THEME_PREFS, Context.MODE_PRIVATE).edit()
            .putString("theme", CallTheme.CUSTOM_ID).commit()
        val loaded = loadCallTheme(context)
        assertEquals(CallTheme.CUSTOM_ID, loaded.id)
        assertEquals(customCallTheme(CallTheme.DEFAULT_CUSTOM_HUE).topArgb, loaded.topArgb)
    }
}
