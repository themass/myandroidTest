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

    var labForced: Boolean
        get() = prefs.getBoolean(KEY_LAB_FORCED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_LAB_FORCED, value).apply()
        }

    companion object {
        const val PREFS = "companion_dh_lab"
        private const val KEY_ENGINE = "companion.dh_engine"
        private const val KEY_LAB_FORCED = "companion.dh_lab"
    }
}
