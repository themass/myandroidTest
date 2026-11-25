package com.openapi.ks.myapp.dh

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.openapi.ks.myapp.avatar.AvatarCommand
import java.io.ByteArrayInputStream

/**
 * WebView 场景引擎的公共骨架（VRM 之外的官方引擎共用）：伪源拦截、白名单供给、
 * `window.dhCommand` 桥、ready 前排队。与 [VrmDigitalHumanEngine] 同一套纪律：
 * 页面全部离线内置，伪源之外的请求一律拒绝。
 *
 * 每个引擎的差异只在：assets 前缀、白名单 [assetFor]、指令映射 [bridge]。
 */
@SuppressLint("SetJavaScriptEnabled") // 页面全部离线内置，只跑我们自己的 assets，无第三方内容。
abstract class WebViewSceneEngine(
    private val host: String,
    private val startPage: String,
    private val bridge: (AvatarCommand) -> AvatarSceneCommand?,
) : DigitalHumanEngine {

    private var webView: WebView? = null
    private var controller: AvatarSceneController? = null
    private var released = false

    /** 页面里的文件 → assets 路径；不在白名单回 null（空响应）。 */
    protected abstract fun resolveAsset(path: String): String?

    override fun bind(spec: com.openapi.ks.myapp.avatar.AvatarSpec) = Unit

    override fun dispatch(command: AvatarCommand) {
        controller?.onCommand(command)
    }

    override fun release() {
        if (released) return
        released = true
        controller = null
        val wv = webView ?: return
        webView = null
        // destroy 必须在主线程、且已脱离窗口树（AndroidView 已负责 detach，这里兜底）。
        wv.post {
            (wv.parent as? android.view.ViewGroup)?.removeView(wv)
            wv.destroy()
        }
    }

    override fun createView(context: Context): View? {
        if (released) return null
        val wv = WebView(context)
        wv.setBackgroundColor(Color.TRANSPARENT)
        wv.settings.javaScriptEnabled = true
        wv.settings.allowFileAccess = false
        val ctrl = AvatarSceneController(bridge = bridge, send = { js -> wv.post { wv.evaluateJavascript(js, null) } })
        controller = ctrl
        wv.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun onReady() { wv.post { ctrl.onPageReady() } }

                @JavascriptInterface
                fun onError(message: String?) {
                    // 页面自己会在画面上显示状态文本；引擎侧不需要额外动作。
                }
            },
            "DhSceneNative",
        )
        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                val url = request.url.toString()
                if (!url.startsWith("https://$host/")) return BLOCKED
                val path = url.substring("https://$host/".length).substringBefore('?').substringBefore('#')
                val asset = resolveAsset(if (path.isEmpty()) "index.html" else path)
                    ?: return BLOCKED
                return try {
                    val mime = MIME[path.substringAfterLast('.')] ?: "application/octet-stream"
                    mime(mime, context.assets.open(asset).use { it.readBytes() })
                } catch (e: java.io.IOException) {
                    BLOCKED
                }
            }
        }
        wv.loadUrl("https://$host/$startPage")
        webView = wv
        return wv
    }

    private fun mime(type: String, body: ByteArray): WebResourceResponse =
        WebResourceResponse(type, "utf-8", ByteArrayInputStream(body))

    protected companion object {
        /** 伪源之外的请求一律拒绝：实验田不联网。 */
        val BLOCKED = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        val MIME = mapOf(
            "html" to "text/html",
            "js" to "application/javascript",
            "json" to "application/json",
            "atlas" to "text/plain",
            "png" to "image/png",
            "glb" to "model/gltf-binary",
        )
    }
}
