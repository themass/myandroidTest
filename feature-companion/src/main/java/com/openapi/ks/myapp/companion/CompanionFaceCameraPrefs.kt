package com.openapi.ks.myapp.companion

import android.content.Context

/**
 * 通话页是否显示人脸跟踪相机叠层（**默认关**）。
 *
 * 与 [CompanionCallScreen] 底部 `cameraAvailable`（LiveKit 视频）无关；
 * 仅控制数字人舞台上的 debug 小窗 / 未来 PiP。
 */
class CompanionFaceCameraPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    companion object {
        private const val PREFS = "companion_face_camera"
        private const val KEY_ENABLED = "enabled"
    }
}
