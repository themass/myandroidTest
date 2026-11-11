package com.openapi.common.permission

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** 单个权限当前所处的状态。 */
enum class PermissionState {
    /** 已授权。 */
    GRANTED,

    /** 从未申请过，可以直接弹系统授权框。 */
    NOT_REQUESTED,

    /** 用户拒绝过一次，还能再弹系统框；再弹之前应先解释为什么需要。 */
    DENIED,

    /**
     * 用户勾选了“不再询问”（或系统已替用户做了这个决定），系统授权框不会再出现，
     * 只能引导用户去应用设置页手动开启。
     */
    PERMANENTLY_DENIED,
}

/**
 * 判断权限状态。
 *
 * 系统的 shouldShowRequestPermissionRationale 在“从未申请过”和“永久拒绝”两种情况下都返回 false，
 * 无法区分——这正是很多 App 拒绝一次后就再也弹不出授权框、也不提示用户去设置的根源。
 * 所以这里额外记录“是否曾经申请过”，组合起来才能得到准确的四种状态。
 */
object PermissionStates {

    private const val PREFS = "core_permission_requested"

    @JvmStatic
    fun of(activity: Activity, permission: String): PermissionState {
        if (isGranted(activity, permission)) return PermissionState.GRANTED
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
            return PermissionState.DENIED
        }
        return if (wasRequested(activity, permission)) {
            PermissionState.PERMANENTLY_DENIED
        } else {
            PermissionState.NOT_REQUESTED
        }
    }

    @JvmStatic
    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    @JvmStatic
    fun allGranted(context: Context, permissions: Array<out String>): Boolean =
        permissions.all { isGranted(context, it) }

    internal fun markRequested(context: Context, permissions: Array<out String>) {
        val editor = prefs(context).edit()
        permissions.forEach { editor.putBoolean(it, true) }
        editor.apply()
    }

    private fun wasRequested(context: Context, permission: String): Boolean =
        prefs(context).getBoolean(permission, false)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
