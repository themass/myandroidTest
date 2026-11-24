package com.openapi.ks.myapp.dh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Spine / TalkingHead 两个 WebView 官方引擎的可用性、白名单与生命周期。
 * 素材随 APK assets 走（dh-spine / dh-talkinghead），没有下载包：availability 只查
 * assets 里页面与 bundle 在不在，菜单行永远直接换舞台、不进下载闸门。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WebSceneEnginesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DhEngineRegistry.resetForTests()
        DhLabBootstrap.resetForTests()
        DhLabBootstrap.ensureDefaults()
    }

    @Test
    fun spine_is_registered_available_and_skips_download_gate() {
        val row = DhEngineRegistry.available(context).first { it.id == DhEngineId.SPINE }
        assertTrue("spine must be available from APK assets: ${row.reason}", row.available)
        assertNull(row.reason)
        assertFalse(row.needsDownload)

        val menuRow = dhEngineMenuRows(listOf(row)).first { it.id == DhEngineId.SPINE }
        assertTrue(menuRow.switches)
        val gate = DhEngineDownloadGate(
            availability = { false },
            ensure = { _, _ -> Result.failure(IllegalStateException("must not download")) },
        )
        assertEquals(EngineClickAction.SELECT, gate.actionFor(menuRow))
    }

    @Test
    fun talking_head_is_registered_available_and_skips_download_gate() {
        val row = DhEngineRegistry.available(context).first { it.id == DhEngineId.TALKING_HEAD }
        assertTrue("talkinghead must be available from APK assets: ${row.reason}", row.available)
        assertNull(row.reason)
        assertFalse(row.needsDownload)
        // assets()==null 钉死：这两个引擎不走下载包（素材在 APK assets）。
        assertNull(DhEngineId.TALKING_HEAD.assets())
        assertNull(DhEngineId.SPINE.assets())
    }

    @Test
    fun spine_asset_whitelist_serves_only_its_own_files() {
        assertEquals("dh-spine/index.html", SpineDigitalHumanEngine.assetFor("index.html"))
        assertEquals("dh-spine/spine.bundle.js", SpineDigitalHumanEngine.assetFor("spine.bundle.js"))
        assertEquals("dh-spine/spineboy-pro.json", SpineDigitalHumanEngine.assetFor("spineboy-pro.json"))
        assertEquals("dh-spine/spineboy-pma.atlas", SpineDigitalHumanEngine.assetFor("spineboy-pma.atlas"))
        assertEquals("dh-spine/spineboy-pma.png", SpineDigitalHumanEngine.assetFor("spineboy-pma.png"))
        // 白名单外一律 null（引擎回空响应）：实验田不联网，也不放行任意 assets 读取。
        assertNull(SpineDigitalHumanEngine.assetFor(""))
        assertNull(SpineDigitalHumanEngine.assetFor("../avatars/demo-avatar-v1.zip"))
        assertNull(SpineDigitalHumanEngine.assetFor("evil.js"))
    }

    @Test
    fun talking_head_asset_whitelist_serves_only_its_own_files() {
        assertEquals("dh-talkinghead/index.html", TalkingHeadDigitalHumanEngine.assetFor("index.html"))
        assertEquals("dh-talkinghead/talkinghead.bundle.js", TalkingHeadDigitalHumanEngine.assetFor("talkinghead.bundle.js"))
        assertEquals("dh-talkinghead/mpfb.glb", TalkingHeadDigitalHumanEngine.assetFor("mpfb.glb"))
        // vendored talkinghead.mjs 顶层 import.meta.url 定位 audio worklet，必须放行同源取文件。
        assertEquals("dh-talkinghead/playback-worklet.js", TalkingHeadDigitalHumanEngine.assetFor("playback-worklet.js"))
        assertNull(TalkingHeadDigitalHumanEngine.assetFor("evil.js"))
    }

    @Test
    fun spine_create_view_lifecycle() {
        val engine = SpineDigitalHumanEngine()
        assertNotNull(engine.createView(context))
        engine.dispatch(com.openapi.ks.myapp.avatar.AvatarCommand.Gesture("walk"))
        engine.release()
        engine.release()
        assertNull(engine.createView(context))
    }

    @Test
    fun talking_head_create_view_lifecycle() {
        val engine = TalkingHeadDigitalHumanEngine()
        assertNotNull(engine.createView(context))
        engine.dispatch(com.openapi.ks.myapp.avatar.AvatarCommand.Emotion("joy"))
        engine.release()
        engine.release()
        assertNull(engine.createView(context))
    }

    @Test
    fun spine_capabilities_are_gesture_only() {
        assertEquals(
            setOf(com.openapi.ks.myapp.avatar.AvatarCapability.GESTURE),
            SpineDigitalHumanEngine().capabilities,
        )
    }

    @Test
    fun talking_head_capabilities_cover_mouth_emotion_gaze() {
        assertEquals(
            setOf(
                com.openapi.ks.myapp.avatar.AvatarCapability.MOUTH,
                com.openapi.ks.myapp.avatar.AvatarCapability.EMOTION,
                com.openapi.ks.myapp.avatar.AvatarCapability.GAZE,
            ),
            TalkingHeadDigitalHumanEngine().capabilities,
        )
    }
}
