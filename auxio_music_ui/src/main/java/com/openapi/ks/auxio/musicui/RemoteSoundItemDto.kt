package com.openapi.ks.auxio.musicui

/**
 * Gson 解析用，字段需与宿主 [com.openapi.ks.myapp.bean.vo.SoundItemsVo] 及接口 JSON 一致。
 */
internal data class RemoteSoundItemDto(
    var name: String? = null,
    var file: String? = null,
    var fileDate: String? = null,
)
