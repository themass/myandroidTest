package com.openapi.common.ui.permission

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [combineStates] 的表驱动单测。
 *
 * 这个函数决定「一次多权限请求」的最终结论，错了会导致两种相反的线上问题：
 * - 把 DENIED 当成 PERMANENTLY_DENIED：用户再点一次，我们直接把他甩去设置页，但系统框其实还能弹；
 * - 把 PERMANENTLY_DENIED 当成 DENIED：调用方提示「再点一次试试」，而系统框已经弹不出来了。
 */
class PermissionOutcomeTest {

    private val g = PermissionState.GRANTED
    private val n = PermissionState.NOT_REQUESTED
    private val d = PermissionState.DENIED
    private val p = PermissionState.PERMANENTLY_DENIED

    @Test
    fun emptyRequestIsGranted() {
        // 低版本没有这个权限（如 Android 13 以下的通知）时，调用方不该被拦
        assertEquals(g, combineStates(emptyList()))
    }

    @Test
    fun allGrantedIsGranted() {
        assertEquals(g, combineStates(listOf(g)))
        assertEquals(g, combineStates(listOf(g, g, g)))
    }

    @Test
    fun anyPermanentDenialWinsOverEverythingElse() {
        // 一项永久拒绝，整体就不可能成功——必须优先于「还拒绝着」和「没申请过」
        assertEquals(p, combineStates(listOf(g, d, p)))
        assertEquals(p, combineStates(listOf(n, p)))
        assertEquals(p, combineStates(listOf(d, p)))
    }

    @Test
    fun anyDenialBeatsNotRequested() {
        // 还没申请过的权限下肯定要弹框；已拒绝过的也要弹，所以整体是「可以再问」
        assertEquals(d, combineStates(listOf(n, d)))
        assertEquals(d, combineStates(listOf(g, d)))
    }

    @Test
    fun untouchedPermissionsAreNotRequested() {
        assertEquals(n, combineStates(listOf(n, n)))
    }
}
