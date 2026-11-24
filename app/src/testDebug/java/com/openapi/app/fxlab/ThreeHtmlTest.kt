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
}
