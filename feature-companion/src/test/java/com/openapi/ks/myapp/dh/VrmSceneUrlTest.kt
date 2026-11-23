package com.openapi.ks.myapp.dh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * WebView 资源拦截的纯映射：https://dh-vrm.local/<path> → <path>。
 * 页面和 JS 从 assets 出，/model 由引擎映射到 AssetStore 缓存的 .vrm 文件。
 */
class VrmSceneUrlTest {

    @Test
    fun scene_origin_urls_resolve_to_their_path() {
        assertEquals("index.html", VrmSceneUrl.pathFor("https://dh-vrm.local/index.html"))
        assertEquals("three-vrm.bundle.js", VrmSceneUrl.pathFor("https://dh-vrm.local/three-vrm.bundle.js"))
        assertEquals("model", VrmSceneUrl.pathFor("https://dh-vrm.local/model"))
        assertEquals("index.html", VrmSceneUrl.pathFor("https://dh-vrm.local/"))
    }

    @Test
    fun foreign_urls_are_not_mine() {
        assertNull(VrmSceneUrl.pathFor("https://example.com/index.html"))
        assertNull(VrmSceneUrl.pathFor("http://dh-vrm.local/index.html"))
        assertNull(VrmSceneUrl.pathFor("file:///android_asset/dh-vrm/index.html"))
    }
}
