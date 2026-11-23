package com.openapi.ks.myapp.companion

import android.content.Context

/** 背景主题的存储位；与波形样式分开存，键格式同套路（存枚举 name）。 */
internal const val CALL_THEME_PREFS = "companion_wave"

/** 自定义主题的色相存在这里（theme 键只存 id，坏值可独立降级）。 */
internal const val CALL_THEME_HUE_KEY = "theme_hue"

internal fun loadCallTheme(context: Context): CallTheme {
    val prefs = context.getSharedPreferences(CALL_THEME_PREFS, Context.MODE_PRIVATE)
    return when (val name = prefs.getString("theme", null)) {
        CallTheme.CUSTOM_ID -> {
            // 色相丢了（旧版本/写坏）就用兜底色相，id 保持 custom 让 UI 打勾打在自定义上。
            val hue = prefs.getFloat(CALL_THEME_HUE_KEY, CallTheme.DEFAULT_CUSTOM_HUE)
            customCallTheme(hue)
        }
        else -> CallTheme.fromName(name)
    }
}

internal fun saveCallTheme(context: Context, theme: CallTheme) {
    context.getSharedPreferences(CALL_THEME_PREFS, Context.MODE_PRIVATE).edit()
        .putString("theme", theme.id)
        .apply {
            val hue = theme.hueDegrees
            if (hue != null) putFloat(CALL_THEME_HUE_KEY, hue)
        }
        .apply()
}
