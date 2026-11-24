package com.openapi.app.fxlab

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

/** Offline asset whitelist: the fake origin is never allowed to reach the network. */
object FxLabThreeUrls {
    const val START_URL = "https://fx-lab.local/three.html"
    val ALLOWED: Set<String> = setOf("three.html", "three.bundle.js")

    fun pathFor(url: String): String? {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host != "fx-lab.local") return null
        val path = uri.path ?: return null
        val name = path.removePrefix("/")
        return name.takeIf { it in ALLOWED && !it.contains("/") }
    }
}

/** Cues sent before onReady are dropped; no stale command queue survives page loading. */
@Composable
fun ThreeOverlay(active: Boolean, command: String?, modifier: Modifier) {
    var failure by remember { mutableStateOf<String?>(null) }
    if (!active) return
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val state = BridgeState()
            WebView(context).apply {
                tag = state
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onReady() { post { state.ready = true } }
                }, "AndroidFxLab")
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                        val path = FxLabThreeUrls.pathFor(request.url.toString()) ?: return BLOCKED
                        return runCatching {
                            val body = context.assets.open("fx-lab/$path").use { it.readBytes() }
                            mime(if (path == "three.html") "text/html" else "application/javascript", body)
                        }.onFailure { failure = it.message ?: it.javaClass.simpleName }
                            .getOrDefault(BLOCKED)
                    }
                }
                loadUrl(FxLabThreeUrls.START_URL)
            }
        },
        update = { view ->
            val state = view.tag as BridgeState
            // New cue is intentionally discarded until the handshake completes.
            if (state.ready && command != null && command != state.lastCommand) {
                state.lastCommand = command
                view.evaluateJavascript("FxLab.command(${jsonQuote(command)})", null)
            }
        },
        onRelease = { it.stopLoading(); it.destroy() },
    )
    failure?.let { Text("Three.js 不可用：$it", modifier, color = ComposeColor.Red) }
}

private class BridgeState {
    @Volatile var ready = false
    var lastCommand: String? = null
}

private fun mime(type: String, body: ByteArray) =
    WebResourceResponse(type, "utf-8", ByteArrayInputStream(body))

private fun jsonQuote(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

private val BLOCKED = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
