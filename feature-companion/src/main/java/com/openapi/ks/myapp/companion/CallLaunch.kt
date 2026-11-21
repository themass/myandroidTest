package com.openapi.ks.myapp.companion

/**
 * 「进入通话」的权限决策：有录音权限就直接开麦进通话；没有就先申请，
 * 等系统回调回来再决定（拒绝也能进，只是静音形态，见 CompanionRoute 的回调）。
 *
 * 抽成纯函数是因为 P0-4 之后有两个入口要用同一套决策：陪伴页自己的通话按钮，
 * 和聊天页跳过来的电话入口。两边的表现必须一模一样，不然用户会以为权限坏了。
 */
fun launchCallWithMic(
    hasMicPermission: Boolean,
    startCall: (micAllowed: Boolean) -> Unit,
    requestMic: () -> Unit,
) {
    if (hasMicPermission) {
        startCall(true)
    } else {
        requestMic()
    }
}
