package com.openapi.ks.myapp.dh

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * 进程级形象熔断。native 渲染引擎（Duix / Live2D / Tao）崩在 SIGSEGV 里 Java 层拦不住，
 * 唯一的通用防线是「上次死在哪，这次就别再去」：
 *
 * - [markActive] 在某个 native 引擎形象页激活时写脏标记（SIMPLE 纯 Compose，不需要）；
 * - [markClean] 在视图正常销毁时清掉；
 * - 进程非正常死亡（native crash）时脏标记残留；下次冷启动 [onColdStart] 发现它，就把
 *   该引擎隔离（quarantine）——[com.openapi.ks.myapp.dh.DigitalHumanLabHost] 查
 *   [isQuarantined] 直接降级 SIMPLE（最基础的无形象页面），不再尝试加载它；
 * - 隔离持久（活过那次重启，重启后仍生效），直到用户在引擎菜单里主动再选该引擎
 *   （[clear]，等于「我知道有风险，再试一次」）。
 *
 * 只隔离最近一个出事的引擎：一次崩溃只能定位到一个引擎，没必要扩大化。
 */
class DhEngineWatchdog(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 形象页激活（native 引擎即将开始渲染）。 */
    fun markActive(engine: DhEngineId) {
        if (engine == DhEngineId.SIMPLE) return
        prefs.edit().putString(KEY_ACTIVE, engine.name).apply()
    }

    /** 形象页正常销毁：本次进程在形象页死掉的概率归零。 */
    fun markClean() {
        prefs.edit().remove(KEY_ACTIVE).apply()
    }

    /**
     * 冷启动调用一次：上次死在形象页 → 隔离该引擎并返回它（同时留一份可展示的原因），
     * 否则返回 null。
     */
    fun onColdStart(lastExitReason: () -> Int? = ::systemLastExitReason): DhEngineId? {
        val raw = prefs.getString(KEY_ACTIVE, null) ?: return null
        val engine = runCatching { DhEngineId.valueOf(raw) }.getOrNull()
        markClean()
        if (engine == null || engine == DhEngineId.SIMPLE) return null
        // 用户 force-stop / 划掉进程 / adb 调试装包不是 native 崩溃，不该因此隔离引擎。
        // 原因未知（API<30 或查不到）时宁可隔离：误隔离只是多点一次菜单，漏掉就是反复崩。
        val reason = lastExitReason()
        if (reason == EXIT_USER_REQUESTED || reason == EXIT_USER_STOPPED) {
            Log.i(TAG, "上次进程是被用户/系统主动结束（reason=$reason），不隔离 ${engine.name}")
            return null
        }
        Log.w(TAG, "上次进程在 ${engine.name} 形象页异常退出 —— 本次会话隔离该引擎，降级无形象页面")
        prefs.edit().putString(KEY_QUARANTINED, engine.name).apply()
        return engine
    }

    /** 上一次进程的 ApplicationExitInfo.reason（API 30+）；查不到返回 null。 */
    private fun systemLastExitReason(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
        return runCatching {
            // 包名下有 isolated 子进程（广告/WebView）的退出记录，要多取几条再按进程名筛。
            val records = am.getHistoricalProcessExitReasons(null, 0, 10)
                .map { ExitRecord(it.processName, it.reason) }
            mainProcessExitReason(records, appContext.packageName)
        }.getOrNull()
    }

    fun isQuarantined(engine: DhEngineId): Boolean =
        prefs.getString(KEY_QUARANTINED, null) == engine.name

    /** 用户主动重新选择该引擎 = 解除隔离（再出事会重新熔断）。 */
    fun clear(engine: DhEngineId) {
        if (isQuarantined(engine)) prefs.edit().remove(KEY_QUARANTINED).apply()
    }

    /** 一条进程退出记录（从 ApplicationExitInfo 抽出，便于单测）。 */
    data class ExitRecord(val processName: String, val reason: Int)

    companion object {
        /** 列表按时间从新到旧；只认主进程（进程名 == 包名）那一条。 */
        fun mainProcessExitReason(records: List<ExitRecord>, mainProcess: String): Int? =
            records.firstOrNull { it.processName == mainProcess }?.reason

        const val PREFS = "companion_dh_watchdog"

        /** ApplicationExitInfo.REASON_*（直接写值，避免 API<30 的类加载问题）。 */
        const val EXIT_CRASH_NATIVE = 5
        const val EXIT_USER_REQUESTED = 10
        const val EXIT_USER_STOPPED = 11
        private const val KEY_ACTIVE = "companion.dh.active_engine"
        private const val KEY_QUARANTINED = "companion.dh.quarantined_engine"
        private const val TAG = "DhEngineWatchdog"
    }
}
