package com.openapi.ks.myapp.dh

import com.openapi.ks.dh.AssetManifest
import com.openapi.ks.dh.EngineAssets

/** 菜单下行尾括号里的说明：区分在线下载 vs 只能 seed/adb。 */
fun dhEngineMenuDetail(id: DhEngineId, available: Boolean, reason: String?, needsDownload: Boolean): String? {
    if (available) return null
    if (needsDownload) {
        val assets = id.assets()
        if (assets != null && !AssetManifest.hasDownloadablePacks(assets)) {
            return when (id) {
                DhEngineId.LIVE2D -> "未下载（无在线包，需 seed live2d）"
                DhEngineId.TAO_AVATAR -> "未下载（模型需 adb 推送）"
                else -> reason ?: "未下载"
            }
        }
    }
    return reason
}

/** 点菜单后的 toast / 不可选提示。 */
fun dhEngineSeedHint(id: DhEngineId): String? = when (id) {
    DhEngineId.LIVE2D -> "Live2D 请在本机执行：scripts/dh-lab/seed-assets.sh live2d（并 push native .so）"
    DhEngineId.TAO_AVATAR -> "Tao 模型目录需 adb 推送；可先点选触发 native 包下载"
    else -> null
}

fun mapEnsureFailureMessage(id: DhEngineId, raw: String?): String {
    val r = raw.orEmpty()
    if (r.contains("No downloadable assets", ignoreCase = true)) {
        return dhEngineSeedHint(id) ?: "暂无在线素材包"
    }
    return r.ifBlank { "下载失败" }
}
