package com.openapi.feature.camerafx

import android.content.Context

/**
 * 全屏 lab 的叠加选项持久化（debug 包内使用；默认显示线框 + pat_head）。
 */
class CameraFxLabPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var overlayPolicy: CameraFxOverlayPolicy
        get() {
            val asset = prefs.getString(KEY_ASSET, StickerAssetIds.PAT_HEAD) ?: StickerAssetIds.PAT_HEAD
            val safeAsset = if (asset in StickerAssetIds.ALLOWED) asset else StickerAssetIds.PAT_HEAD
            return CameraFxOverlayPolicy(
                drawWireframe = prefs.getBoolean(KEY_WIREFRAME, true),
                drawStickers = prefs.getBoolean(KEY_STICKERS, true),
                stickerAssetId = safeAsset,
                showDebugHud = prefs.getBoolean(KEY_HUD, true),
            )
        }
        set(value) {
            prefs.edit()
                .putBoolean(KEY_WIREFRAME, value.drawWireframe)
                .putBoolean(KEY_STICKERS, value.drawStickers)
                .putString(KEY_ASSET, value.stickerAssetId)
                .putBoolean(KEY_HUD, value.showDebugHud)
                .apply()
        }

    companion object {
        private const val PREFS = "camera_fx_lab"
        private const val KEY_WIREFRAME = "wireframe"
        private const val KEY_STICKERS = "stickers"
        private const val KEY_ASSET = "sticker_asset"
        private const val KEY_HUD = "hud"
    }
}
