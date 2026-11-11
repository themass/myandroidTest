package com.openapi.common.ui.permission

/**
 * 把一组 [PermissionState] 合并成一个整体结论。
 *
 * 纯函数，不碰 Android API，方便单测覆盖「部分授权」的组合：
 * - 空集合（当前系统不需要权限）→ GRANTED；
 * - 全部 GRANTED → GRANTED；
 * - 有任何一个永久拒绝 → PERMANENTLY_DENIED（这一项弹不出系统框，整体不可能成功）；
 * - 否则有任何一个拒绝 → DENIED（下一次还能再问）；
 * - 剩下就是都没申请过 → NOT_REQUESTED。
 */
internal fun combineStates(states: List<PermissionState>): PermissionState = when {
    states.isEmpty() -> PermissionState.GRANTED
    states.all { it == PermissionState.GRANTED } -> PermissionState.GRANTED
    states.any { it == PermissionState.PERMANENTLY_DENIED } -> PermissionState.PERMANENTLY_DENIED
    states.any { it == PermissionState.DENIED } -> PermissionState.DENIED
    else -> PermissionState.NOT_REQUESTED
}
