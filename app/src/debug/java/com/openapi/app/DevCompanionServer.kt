package com.openapi.app

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.openapi.ks.myapp.companion.CompanionConfig

/**
 * **debug 源集专用**（`app/src/debug/`）：把陪伴 API 的 base URL 指到本地栈，供真机验收用。
 *
 * 为什么要它（runbook `local-livekit-device-runbook.md` §1）：陪伴 API 的地址写死在
 * `Constants.BASE_IP`，仓库里没有 dev 菜单 / env / flavor 入口，所以「把手机指到我 Mac 上
 * 那套 companion-agent」以前只能改代码再打包装机。这里给它一个装机后可改的口子：
 *
 * ```
 * adb shell run-as com.openapi.ks.chat   # 写 app 私有 prefs
 *   <string name="companion_base_url">http://127.0.0.1:8081</string>
 * ```
 *
 * 三道边界，缺一都不算 debug-only：
 * 1. 这个类在 **debug 源集**，release 的编译输入里压根没有它；
 * 2. 声明它的 `<provider>` 在 `app/src/debug/AndroidManifest.xml`，release 清单合并不到；
 * 3. 运行期还额外按 `FLAG_DEBUGGABLE` 判一次（与 [com.openapi.ks.myapp.companion.LiveKitCompanionTransport]
 *    的 debug 判定同一个惯例）——即使有人把它挪进 main，release 包读不到 prefs 之外的任何东西。
 *
 * APK 里**不含任何环境地址**：地址只在装机后存在于 app 私有 prefs 里，卸载即清。
 * `Constants.BASE_IP` 不动：频道、放映厅等功能照常走生产。
 *
 * 用 `<provider>` 而不是 Application 里的一行：provider 在 `Application.onCreate` **之前**
 * 创建，陪伴页第一次读 `CompanionConfig.baseUrl` 一定已经拿到覆盖值，不需要给 app 加启动时序约束。
 * 它不提供任何数据，`query` 等一律空实现。
 */
class DevCompanionServer : ContentProvider() {

    companion object {
        /** app 私有 prefs（与 `PreferenceUtils` 同一个文件），键名与 runbook §4.0 一致。 */
        private const val PREFS = "app_preferences"
        const val KEY_BASE_URL = "companion_base_url"
    }

    override fun onCreate(): Boolean {
        val context: Context = context ?: return false
        if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            return false
        }
        val url = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BASE_URL, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return false
        // 只接受 http(s)：写错一个字母（比如漏了 scheme）时宁可回生产地址，也不要让陪伴页
        // 报一句看不出所以然的连接失败。
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        CompanionConfig.debugOverride = url
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
