package com.openapi.common.ui.permission

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.openapi.common.R
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * 按需申请运行时权限，在功能入口（点录音、点拍照……）处调用，而不是启动时一次性申请一堆。
 *
 * 完整流程：
 * 1. 已全部授权 → 直接回调 [Callback.onGranted]。
 * 2. 用户拒绝过一次（还能再问）→ 先弹解释对话框，用户点“继续”后再弹系统授权框。
 * 3. 从未申请过 → 直接弹系统授权框。
 * 4. 系统框返回后仍有被拒的：
 *    - 已是“不再询问” → 弹“去设置”对话框，从设置页返回后自动重新检查；
 *    - 否则 → 回调 [Callback.onDenied]。
 *
 * 关键点：永久拒绝后**每次**触发功能都会再次弹出“去设置”的引导，而不是只提示一次，
 * 用户不会因为某次误点“不再询问”就永远用不了这个功能。
 *
 * 使用 ActivityResultRegistry 动态注册，因此可以在任意时刻调用（不像 registerForActivityResult
 * 必须在 onCreate 之前注册），Java 代码里也能直接用：
 * ```java
 * PermissionRequester.with(this)
 *         .permissions(Permissions.RECORD_AUDIO)
 *         .rationale("发送语音消息需要使用麦克风")
 *         .request(new PermissionRequester.Callback() { ... });
 * ```
 */
class PermissionRequester private constructor(private val activity: ComponentActivity) {

    /** 用抽象类而不是带默认方法的 interface：Kotlin 接口默认方法对 Java 实现方不可见。 */
    abstract class Callback {
        abstract fun onGranted()

        /** 用户本次拒绝了（或从设置页回来仍未开启）。默认什么都不做。 */
        open fun onDenied(denied: List<String>) {}

        /**
         * 永久拒绝，且用户没有去设置（或在设置里仍没开启）。默认退化成 [onDenied]，
         * 需要区分「再点一次还能问」和「必须去设置」的调用方覆盖它。
         */
        open fun onPermanentlyDenied(denied: List<String>) = onDenied(denied)
    }

    private var permissions: Array<out String> = emptyArray()
    private var rationale: String = ""

    /**
     * 协程版本，等价于 [request]，但结果直接返回给调用方。
     *
     * 和回调版本共用同一套流程（解释对话框、去设置引导），区别只是永久拒绝不再弹「去设置」框，
     * 而是把 [PermissionOutcome.PermanentlyDenied] 交回去——挂起 API 的调用方通常自己就能显示提示。
     */
    suspend fun await(): PermissionOutcome = suspendCancellableCoroutine { cont ->
        if (permissions.isEmpty()) {
            cont.resume(PermissionOutcome.Granted)
            return@suspendCancellableCoroutine
        }
        request(object : Callback() {
            override fun onGranted() {
                if (cont.isActive) cont.resume(PermissionOutcome.Granted)
            }

            override fun onDenied(denied: List<String>) {
                if (cont.isActive) cont.resume(PermissionOutcome.Denied(denied))
            }

            override fun onPermanentlyDenied(denied: List<String>) {
                if (cont.isActive) cont.resume(PermissionOutcome.PermanentlyDenied(denied))
            }
        })
    }

    fun permissions(vararg permissions: String): PermissionRequester = apply {
        this.permissions = permissions
    }

    /** 向用户解释为什么需要这个权限，会出现在解释对话框和“去设置”对话框里。 */
    fun rationale(rationale: String): PermissionRequester = apply {
        this.rationale = rationale
    }

    fun request(callback: Callback) {
        require(permissions.isNotEmpty()) { "permissions() must be called before request()" }
        if (activity.isFinishing || activity.isDestroyed) return

        if (PermissionStates.allGranted(activity, permissions)) {
            callback.onGranted()
            return
        }

        val states = permissions.map { PermissionStates.of(activity, it) }
        when {
            states.any { it == PermissionState.DENIED } ->
                showRationale { launchSystemRequest(callback) }

            // 所有未授权的都已是“不再询问”，系统框不会再出现，直接引导去设置
            states.filter { it != PermissionState.GRANTED }
                .all { it == PermissionState.PERMANENTLY_DENIED } ->
                showGoToSettings(callback)

            else -> launchSystemRequest(callback)
        }
    }

    private fun launchSystemRequest(callback: Callback) {
        PermissionStates.markRequested(activity, permissions)
        lateinit var launcher: ActivityResultLauncher<Array<String>>
        launcher = activity.activityResultRegistry.register(
            nextKey(),
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { result ->
            launcher.unregister()
            val denied = result.filterValues { granted -> !granted }.keys.toList()
            when {
                denied.isEmpty() -> callback.onGranted()
                denied.any { PermissionStates.of(activity, it) == PermissionState.PERMANENTLY_DENIED } ->
                    showGoToSettings(callback)

                else -> callback.onDenied(denied)
            }
        }
        launcher.launch(permissions.map { it }.toTypedArray())
    }

    private fun showRationale(onContinue: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.core_permission_rationale_title)
            .setMessage(rationale)
            .setPositiveButton(R.string.core_permission_continue) { _, _ -> onContinue() }
            .setNegativeButton(R.string.core_permission_cancel, null)
            .show()
    }

    private fun showGoToSettings(callback: Callback) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.core_permission_go_settings_title)
            .setMessage(activity.getString(R.string.core_permission_go_settings_message, rationale))
            .setPositiveButton(R.string.core_permission_go_settings) { _, _ -> openAppSettings(callback) }
            .setNegativeButton(R.string.core_permission_cancel) { _, _ ->
                callback.onPermanentlyDenied(permissions.filterNot { PermissionStates.isGranted(activity, it) })
            }
            .show()
    }

    /** 跳到本应用的系统设置页；用户回来后重新检查，开启了就直接继续功能，不需要再点一次。 */
    private fun openAppSettings(callback: Callback) {
        lateinit var launcher: ActivityResultLauncher<Intent>
        launcher = activity.activityResultRegistry.register(
            nextKey(),
            ActivityResultContracts.StartActivityForResult(),
        ) {
            launcher.unregister()
            val denied = permissions.filterNot { PermissionStates.isGranted(activity, it) }
            if (denied.isEmpty()) {
                callback.onGranted()
            } else {
                // 从设置页回来仍然没开：系统框依旧不会再出现，按永久拒绝处理，不要谎称「还能再问」
                callback.onPermanentlyDenied(denied)
            }
        }
        launcher.launch(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", activity.packageName, null)),
        )
    }

    companion object {
        private val keySeq = AtomicInteger()

        private fun nextKey() = "core_permission_${keySeq.incrementAndGet()}"

        @JvmStatic
        fun with(activity: ComponentActivity): PermissionRequester = PermissionRequester(activity)
    }
}
