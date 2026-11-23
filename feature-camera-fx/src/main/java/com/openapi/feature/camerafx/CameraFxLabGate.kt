package com.openapi.feature.camerafx

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * demo 入口闸门（与 `com.openapi.ks.myapp.dh.DhLabGate` 同一判定思路）：
 * debuggable 包默认可见；release 包里不出现入口，不进生产导航。
 */
object CameraFxLabGate {
    fun isEnabled(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
