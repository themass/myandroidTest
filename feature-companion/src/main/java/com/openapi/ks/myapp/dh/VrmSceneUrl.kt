package com.openapi.ks.myapp.dh

/**
 * WebView 场景页的伪源（pseudo-origin）：页面从 assets 出、模型从 AssetStore 缓存出，
 * 全部经 [android.webkit.WebViewClient.shouldInterceptRequest] 按这个 origin 拦截，
 * 不开 file:// 、不联网。
 */
object VrmSceneUrl {
    const val HOST = "dh-vrm.local"
    const val START_URL = "https://$HOST/index.html"

    /** AssetStore 缓存里的 .vrm 模型在页面里的地址（引擎拦截后回文件流）。 */
    const val MODEL_URL = "https://$HOST/model"

    /** [START_URL] 同源的 URL → assets 相对路径（"index.html"、"scene.bundle.js"）；`/` 回首页。 */
    fun pathFor(url: String): String? {
        val prefix = "https://$HOST/"
        if (!url.startsWith(prefix)) return null
        val path = url.substring(prefix.length).substringBefore('?').substringBefore('#')
        return if (path.isEmpty()) "index.html" else path
    }
}
