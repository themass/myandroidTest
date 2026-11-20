package com.openapi.ks.myapp.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * P0-4：聊天页的电话入口跳进陪伴页时，要能复用陪伴页自己的「进通话」决策，
 * 所以把这个决策从 CompanionRoute 里抽成纯函数并用测试钉住。
 */
class CallLaunchTest {

    @Test
    fun hasMicPermissionStartsCallWithMicOn() {
        var started: Boolean? = null
        var requested = false
        launchCallWithMic(
            hasMicPermission = true,
            startCall = { started = it },
            requestMic = { requested = true },
        )
        assertEquals("有权限应直接开麦进通话", true, started)
        assertFalse("有权限不该再弹权限申请", requested)
    }

    @Test
    fun withoutMicPermissionRequestsMicInsteadOfStarting() {
        var started: Boolean? = null
        var requested = false
        launchCallWithMic(
            hasMicPermission = false,
            startCall = { started = it },
            requestMic = { requested = true },
        )
        assertEquals("没权限时不能先斩后奏直接进通话", null, started)
        if (!requested) throw AssertionError("没权限必须先申请麦克风")
    }
}
