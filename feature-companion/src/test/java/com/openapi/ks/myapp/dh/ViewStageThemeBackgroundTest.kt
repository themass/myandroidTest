package com.openapi.ks.myapp.dh

import android.view.TextureView
import android.view.View
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ViewStageThemeBackgroundTest {

    @Test
    fun `TextureView rejects theme background`() {
        val ctx = RuntimeEnvironment.getApplication()
        val tv = TextureView(ctx)
        assertFalse(canApplyThemeBackgroundToView(tv))
    }

    @Test
    fun `plain View accepts theme background`() {
        val ctx = RuntimeEnvironment.getApplication()
        val v = View(ctx)
        assertTrue(canApplyThemeBackgroundToView(v))
    }

}
