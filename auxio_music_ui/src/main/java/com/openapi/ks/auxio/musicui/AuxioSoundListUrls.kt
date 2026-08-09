package com.openapi.ks.auxio.musicui

import java.util.Locale

/**
 * 与 [com.openapi.ks.myapp.constant.Constants.API_SOUND_ITEMS_URL] 保持同步：
 * `/sound/items.json?start=%s&limit=30&channel=%s&keyword=%s`
 */
internal object AuxioSoundListUrls {
    private const val RELATIVE =
        "/sound/items.json?start=%s&limit=30&channel=%s&keyword=%s"

    fun buildFullUrl(apiRootNoTrailingSlash: String, start: Int, channel: String, keyword: String): String {
        val root = apiRootNoTrailingSlash.trimEnd('/')
        return String.format(Locale.US, root + RELATIVE, start, channel, keyword)
    }
}
