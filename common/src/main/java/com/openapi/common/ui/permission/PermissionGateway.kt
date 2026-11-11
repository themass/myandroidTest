package com.openapi.common.ui.permission

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * 全应用唯一的权限请求入口。所有「点一下就要做需要权限的事」的地方都走这里，
 * 不要再自己写 `registerForActivityResult(RequestPermission)` 或
 * `checkSelfPermission` + `requestPermissions`。
 *
 * 为什么需要它：`registerForActivityResult` 必须在 STARTED 之前注册，在 Compose 里只能固定在
 * 组合期注册、再把结果想办法接回点击事件；每个页面各写一套，行为（是否解释、永久拒绝后怎么办）
 * 和文案就会各不相同。这里改用 `ActivityResultRegistry` 按需动态注册，因此可以在任意时刻发起请求。
 *
 * ```kotlin
 * scope.launch {
 *     when (PermissionGateway.of(activity).request(
 *         PermissionRequest(Permissions.CAMERA, "拍照需要相机权限"),
 *     )) {
 *         PermissionOutcome.Granted -> openCamera()
 *         is PermissionOutcome.Denied -> toast("未授予相机权限")
 *         is PermissionOutcome.PermanentlyDenied -> showGoToSettingsHint()
 *     }
 * }
 * ```
 *
 * 同一 Activity 上的请求按到达顺序串行执行：连点两次麦克风按钮不会叠出两个解释对话框，
 * 第二个请求会在第一个结束后复用其已经拿到的授权。
 */
object PermissionGateway {

    private val locks = ConcurrentHashMap<Int, Mutex>()

    private fun lockFor(activity: Activity): Mutex =
        locks.getOrPut(System.identityHashCode(activity)) { Mutex() }

    /**
     * 取得某个宿主 Activity 的请求入口。
     *
     * 只支持 [ComponentActivity]（含 `FragmentActivity`、`AppCompatActivity`）；
     * 老式 `android.app.Activity` 拿不到 `activityResultRegistry`，需要先改基类——
     * 与其在这里各写一套降级逻辑，不如在迁移时就暴露出来。
     */
    fun of(activity: Activity): PermissionGatewayHandle = when (activity) {
        is ComponentActivity -> ComponentPermissionHandle(activity)
        else -> throw IllegalArgumentException(
            "${activity.javaClass.name} 不是 ComponentActivity/FragmentActivity，无法使用统一权限入口；请先把基类换掉",
        )
    }

    /** 一次性检查，不弹框。用于「要不要显示入口」这类判断；真正请求请用 [PermissionGatewayHandle.request]。 */
    fun isGranted(context: Context, request: PermissionRequest): Boolean =
        context.let { ctx -> request.permissions.all { PermissionStates.isGranted(ctx, it) } }

    /** 一次性检查单个权限（不弹框）。 */
    fun isGranted(context: Context, permission: String): Boolean =
        PermissionStates.isGranted(context, permission)

    /**
     * 通知是否真的能弹出来。
     *
     * 光看 `POST_NOTIFICATIONS` 还不够：用户在系统设置里关掉「通知」总开关后权限仍是已授权，
     * 但 `notify()` 会被静默丢弃。判断「要不要提示用户去开通知」时用这个。
     */
    fun canPostNotifications(context: Context): Boolean {
        val manager = androidx.core.app.NotificationManagerCompat.from(context)
        return manager.areNotificationsEnabled()
    }

    /** 打开本应用的系统设置页，供「去设置」按钮使用。 */
    fun goToSettings(context: Context) {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.fromParts("package", context.packageName, null),
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    internal suspend fun <T> serialize(activity: Activity, block: suspend () -> T): T =
        lockFor(activity).withLock { block() }
}

/** 绑定到某个 Activity 的请求入口。 */
interface PermissionGatewayHandle {

    /** 当前状态快照（不弹框）。 */
    fun state(request: PermissionRequest): PermissionState

    /**
     * 检查 → （需要时）解释 → 系统授权框 → 永久拒绝时引导去设置，全部走完才返回。
     * 宿主 Activity 已销毁时按拒绝返回，不会抛异常。请求可被取消（协程取消时挂起结束，系统框仍会显示）。
     */
    suspend fun request(request: PermissionRequest): PermissionOutcome
}

private class ComponentPermissionHandle(
    private val activity: ComponentActivity,
) : PermissionGatewayHandle {

    override fun state(request: PermissionRequest): PermissionState =
        combineStates(request.permissions.map { PermissionStates.of(activity, it) })

    override suspend fun request(request: PermissionRequest): PermissionOutcome {
        if (request.permissions.isEmpty()) return PermissionOutcome.Granted
        if (activity.isFinishing || activity.isDestroyed) {
            return PermissionOutcome.Denied(request.permissions.toList())
        }
        return PermissionGateway.serialize(activity) {
            // 排队期间可能已经被别的请求一并授予了
            if (PermissionStates.allGranted(activity, request.permissions.toTypedArray())) {
                return@serialize PermissionOutcome.Granted
            }
            if (activity.isFinishing || activity.isDestroyed) {
                return@serialize PermissionOutcome.Denied(request.permissions.toList())
            }
            PermissionRequester.with(activity)
                .permissions(*request.permissions.toTypedArray())
                .rationale(request.rationale)
                .await()
        }
    }
}
