package com.openapi.ks.myapp.dh

import com.openapi.ks.myapp.avatar.AvatarCommand

/**
 * 场景页指令时序：页面（JS 侧 [VrmBridge] 线格式的接收端）ready 之前到的指令排队，
 * ready 时按序冲刷；之后的指令直通。[send] 收到的是 `window.vrmCommand('…')` 整句 JS。
 *
 * 纯逻辑（send 注入），主线程调用；与 WebView 的粘合在 [VrmDigitalHumanEngine]。
 */
class VrmSceneController(private val send: (String) -> Unit) {

    private val pending = mutableListOf<VrmSceneCommand>()
    private var ready = false

    fun onCommand(command: AvatarCommand) {
        val scene = VrmBridge.sceneCommand(command) ?: return
        if (ready) send(VrmBridge.jsCall(scene)) else pending += scene
    }

    fun onPageReady() {
        ready = true
        val queued = pending.toList()
        pending.clear()
        queued.forEach { send(VrmBridge.jsCall(it)) }
    }
}
