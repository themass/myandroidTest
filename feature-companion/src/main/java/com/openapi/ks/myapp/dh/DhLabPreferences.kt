package com.openapi.ks.myapp.dh

import android.content.Context

/** Lab 偏好：上次引擎 + 是否强制打开 Lab（非 debuggable 也可开）。 */
class DhLabPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var engine: DhEngineId
        get() = prefs.getString(KEY_ENGINE, null)
            ?.let { runCatching { DhEngineId.valueOf(it) }.getOrNull() }
            ?: DhEngineId.SIMPLE
        set(value) {
            prefs.edit().putString(KEY_ENGINE, value.name).apply()
        }

    /**
     * 通话页右上角下拉选中的引擎。
     *
     * null = 原来的形象（分层立绘 / 圆头像）。与 [engine] 分开：后者默认 SIMPLE，
     * 不能拿来决定要不要盖掉原来的舞台，否则 Lab 一开形象就没了。
     */
    var callStage: DhEngineId?
        get() {
            val raw = prefs.getString(KEY_CALL_STAGE, null) ?: return null
            if (raw.isBlank() || raw == ORIGINAL) return null
            return runCatching { DhEngineId.valueOf(raw) }.getOrNull()
        }
        set(value) {
            prefs.edit().putString(KEY_CALL_STAGE, value?.name ?: ORIGINAL).apply()
        }

    var labForced: Boolean
        get() = prefs.getBoolean(KEY_LAB_FORCED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_LAB_FORCED, value).apply()
        }

    /**
     * Live2D 当前形象（多形象切换，用户 2026-11-23）。存原始字符串、读时过
     * [Live2dModels.sanitize] 白名单——脏值回落 ATRI，永远回一个合法目录名。
     */
    var live2dModel: String
        get() = Live2dModels.sanitize(prefs.getString(KEY_LIVE2D_MODEL, null))
        set(value) {
            prefs.edit().putString(KEY_LIVE2D_MODEL, Live2dModels.sanitize(value)).apply()
        }

    companion object {
        const val PREFS = "companion_dh_lab"
        private const val KEY_ENGINE = "companion.dh_engine"
        private const val KEY_CALL_STAGE = "companion.dh_call_stage"
        private const val KEY_LAB_FORCED = "companion.dh_lab"
        private const val KEY_LIVE2D_MODEL = "companion.dh_live2d_model"
        private const val ORIGINAL = "ORIGINAL"
    }
}
