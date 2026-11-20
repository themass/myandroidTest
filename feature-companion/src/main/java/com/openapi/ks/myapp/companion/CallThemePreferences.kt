package com.openapi.ks.myapp.companion

import android.content.Context

/** 背景主题的存储位；与波形样式分开存，键格式同套路（存枚举 name）。 */
internal const val CALL_THEME_PREFS = "companion_wave"

internal fun loadCallTheme(context: Context): CallTheme =
    context.getSharedPreferences(CALL_THEME_PREFS, Context.MODE_PRIVATE)
        .getString("theme", null)
        .let(CallTheme::fromName)

internal fun saveCallTheme(context: Context, theme: CallTheme) {
    context.getSharedPreferences(CALL_THEME_PREFS, Context.MODE_PRIVATE).edit()
        .putString("theme", theme.id)
        .apply()
}
