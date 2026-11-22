package com.openapi.ks.myapp.dh

import android.content.Context
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.openapi.ks.dh.AssetManifest
import com.openapi.ks.dh.AssetStore
import com.openapi.ks.dh.EngineAssets
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * createView 生命周期：没模型不给 View；有模型给 WebView；release 幂等。
 * Robolectric 的 WebView 不真渲染，钉的是引擎的时序与判空。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VrmEngineViewTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DhEngineRegistry.resetForTests()
        DhLabBootstrap.resetForTests()
        DhLabBootstrap.ensureDefaults()
    }

    private fun seedModel() {
        val spec = AssetManifest.assetsFor(EngineAssets.VRM).first()
        val root = DhLabBootstrap.assetStore(context).cacheRoot(EngineAssets.VRM)
        val dest = File(root, spec.relativePath)
        dest.parentFile!!.mkdirs()
        dest.writeText("fake vrm bytes")
        val marker = File(root, AssetStore.markerRelativePath(spec))
        marker.parentFile!!.mkdirs()
        marker.createNewFile()
    }

    @Test
    fun create_view_without_model_returns_null() {
        val engine = VrmDigitalHumanEngine()
        assertNull(engine.createView(context))
        engine.release()
    }

    @Test
    fun create_view_with_seeded_model_returns_webview_and_dispatch_survives() {
        seedModel()
        val engine = VrmDigitalHumanEngine()
        val view = engine.createView(context)
        assertNotNull(view)
        assertEquals(true, view is WebView)
        // 页面还没 ready（Robolectric 不跑 JS），指令必须排队而不是崩。
        engine.dispatch(com.openapi.ks.myapp.avatar.AvatarCommand.Emotion("joy"))
        engine.release()
        // release 幂等；之后不再给新 View。
        engine.release()
        assertNull(engine.createView(context))
    }
}
