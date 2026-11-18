package com.openapi.ks.myapp.dh

import android.content.Context
import android.content.pm.ApplicationInfo

/** Lab 入口：debuggable 包默认开；或 prefs `companion.dh_lab` 强制开。 */
object DhLabGate {
    fun isEnabled(context: Context): Boolean {
        val debuggable =
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable) return true
        return DhLabPreferences(context).labForced
    }
}
