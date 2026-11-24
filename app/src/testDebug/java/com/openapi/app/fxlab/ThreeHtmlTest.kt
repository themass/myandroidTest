package com.openapi.app.fxlab

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ThreeHtmlTest {

    // 真机（WebView 内核 hwschromium）暴露：打包脚本跑在 <head> 里，document.body 还是
    // null，bundle 在 appendChild 处崩，window.FxLab 永远不存在 → 页面上没有任何效果。
    @Test
    fun bundleScriptLoadsAfterBodyExists() {
        val html = ApplicationProvider.getApplicationContext<android.content.Context>()
            .assets.open("fx-lab/three.html").reader().readText()
        val bodyIndex = html.indexOf("<body")
        val scriptIndex = html.indexOf("<script src=\"three.bundle.js\">")
        assertTrue("three.html must have a <body>", bodyIndex >= 0)
        assertTrue(
            "three.bundle.js must load inside <body>, not <head> (document.body is null there)",
            scriptIndex > bodyIndex,
        )
    }

    // 真机（WebView 内核 hwschromium）第二处：loadUrl 时 WebView 还没布局，首帧
    // innerWidth 为 0 → setSize 后 canvas 只有 1px，圆环永远不可见。bundle 必须
    // 每帧核对窗口尺寸（resize guard），不能只挂 window resize 事件。
    @Test
    fun bundleReChecksWindowSizeEveryFrame() {
        val bundle = ApplicationProvider.getApplicationContext<android.content.Context>()
            .assets.open("fx-lab/three.bundle.js").reader().readText()
        assertTrue(
            "bundle must re-apply setSize when the WebView finishes layout (innerWidth guard in frame loop)",
            bundle.contains("innerWidth") && bundle.contains("innerHeight"),
        )
        val start = bundle.indexOf("window.FxLab")
        assertTrue("FxLab seam must exist", start > 0)
    }

    // 真机（hwschromium）第三处：透明 WebView 里 WebGL 画布整层不参与合成
    // （DOM 绿块可见、render.calls>0、软件层也无效）。方案：WebGL 离屏渲染，
    // 每帧 drawImage 到可见的 2D canvas —— 2D 像素走的是已验证可合成的 DOM 通路。
    @Test
    fun webglIsBlittedIntoDomCanvas2d() {
        val bundle = ApplicationProvider.getApplicationContext<android.content.Context>()
            .assets.open("fx-lab/three.bundle.js").reader().readText()
        assertTrue(
            "bundle must blit the WebGL canvas into a 2D canvas each frame (GL layers do not composite in this WebView)",
            bundle.contains("drawImage"),
        )
    }
}
