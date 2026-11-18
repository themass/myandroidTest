package com.openapi.ks.myapp.dh

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * TaoAvatar 旗舰门闸（设计 §3）：约 8GB+ RAM；不达标标「机型不足」。
 * 不替代 SDK 是否编入——两者同时满足才可用。
 */
object TaoDeviceGate {
    /** 约 7.5GB 起算，兼容厂商「8GB」虚标。 */
    const val MIN_TOTAL_MEM_BYTES: Long = 7_500_000_000L

    data class Verdict(val supported: Boolean, val reason: String?)

    fun evaluate(context: Context): Verdict {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return Verdict(false, "机型不足（无 ActivityManager）")
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        if (info.totalMem < MIN_TOTAL_MEM_BYTES) {
            val gb = info.totalMem / (1024.0 * 1024.0 * 1024.0)
            return Verdict(false, "机型不足（约 ${"%.1f".format(gb)}GB RAM，需 ≥8GB）")
        }
        // arm64 为主力；x86 模拟器也不放行 Tao 真机路径
        val abis = Build.SUPPORTED_ABIS.joinToString()
        if (!abis.contains("arm64")) {
            return Verdict(false, "机型不足（需 arm64，当前 $abis）")
        }
        return Verdict(true, null)
    }
}
