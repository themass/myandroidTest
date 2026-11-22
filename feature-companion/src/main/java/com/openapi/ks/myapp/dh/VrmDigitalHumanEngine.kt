package com.openapi.ks.myapp.dh

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.openapi.ks.dh.AssetManifest
import com.openapi.ks.dh.AssetSpec
import com.openapi.ks.dh.AssetStore
import com.openapi.ks.dh.EngineAssets
import com.openapi.ks.myapp.avatar.AvatarCapability
import com.openapi.ks.myapp.avatar.AvatarCommand
import com.openapi.ks.myapp.avatar.AvatarSpec
import java.io.ByteArrayInputStream
import java.io.File

/**
 * VRM（WebView + three.js + three-vrm）lab 实验田。
 *
 * 渲染全在页面里跑：[assets/dh-vrm] 离线打包（index.html + scene.bundle.js），模型从
 * AssetStore 缓存经伪源 `https://dh-vrm.local/` 拦截供给——不联网、不开 file://。
 * Kotlin→JS 走 [VrmBridge] 的 JSON 线格式；页面里的单指拖动（视线跟随）不回程。
 *
 * 定位是**效果验证**，不是产品件：模型逐件授权（示例模型 personalNonProfit），
 * 不进生产路径；可用性只认「模型文件 + 完成标记在不在 cache」。
 */
@SuppressLint("SetJavaScriptEnabled") // 页面全部离线内置，只跑我们自己的 assets，无第三方内容。
class VrmDigitalHumanEngine : DigitalHumanEngine {

    override val id: DhEngineId = DhEngineId.VRM

    override val capabilities: Set<AvatarCapability> = setOf(
        AvatarCapability.MOUTH,
        AvatarCapability.EMOTION,
        AvatarCapability.GAZE,
    )

    private var webView: WebView? = null
    private var controller: VrmSceneController? = null
    private var released = false

    override fun bind(spec: AvatarSpec) = Unit

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

    override fun availability(context: Context): DhEngineAvailability {
        // 场景页在 APK 里；能不能跑只取决于模型下没下好。native/WebView 缺失是环境问题，
        // 由 createView 失败与看门狗降级兜底，不在这里预判。
        val ready = modelFile(context) != null
        return DhEngineAvailability(
            id = id,
            available = ready,
            reason = if (ready) null else "未下载",
            needsDownload = !ready,
        )
    }

    override fun createView(context: Context): android.view.View? {
        if (released) return null
        val model = modelFile(context) ?: return null
        val wv = WebView(context)
        wv.setBackgroundColor(Color.TRANSPARENT)
        wv.settings.javaScriptEnabled = true
        wv.settings.allowFileAccess = false
        val ctrl = VrmSceneController(send = { js -> wv.post { wv.evaluateJavascript(js, null) } })
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
            "DhVrmNative",
        )
        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                val url = request.url.toString()
                if (url == VrmSceneUrl.MODEL_URL) {
                    return mime("application/octet-stream", model.readBytes())
                }
                val path = VrmSceneUrl.pathFor(url) ?: return BLOCKED
                return when (path) {
                    "index.html" -> mime("text/html", context.assets.read("dh-vrm/$path"))
                    "scene.bundle.js" -> mime("application/javascript", context.assets.read("dh-vrm/$path"))
                    else -> BLOCKED
                }
            }
        }
        wv.loadUrl(VrmSceneUrl.START_URL)
        webView = wv
        return wv
    }

    private fun mime(type: String, body: ByteArray): WebResourceResponse =
        WebResourceResponse(type, "utf-8", ByteArrayInputStream(body))

    private fun modelFile(context: Context): File? {
        val spec = AssetManifest.assetsFor(EngineAssets.VRM).firstOrNull() ?: return null
        val root = DhLabBootstrap.assetStore(context).cacheRoot(EngineAssets.VRM)
        val file = File(root, spec.relativePath)
        val marker = File(root, AssetStore.markerRelativePath(spec))
        return file.takeIf { it.isFile && marker.isFile }
    }

    private fun android.content.res.AssetManager.read(path: String): ByteArray =
        open(path).use { it.readBytes() }

    private companion object {
        /** 伪源之外的请求一律拒绝：实验田不联网。 */
        val BLOCKED = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }
}
