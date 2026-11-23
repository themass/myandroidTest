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

    /** 轨道累计角（度）：手势给增量，这里累加成绝对角（见 [VrmSceneCommand.Orbit]）。 */
    private var yawDeg = 0f

    fun onCommand(command: AvatarCommand) {
        val scene = VrmBridge.sceneCommand(command) ?: return
        queue(scene)
    }

    /** 一笔拖动的水平增量（度）。60Hz 的中间态在页面侧合并，只发最后一个累计值。 */
    fun onOrbitDelta(deltaDeg: Float) {
        yawDeg += deltaDeg
        if (ready) {
            send(VrmBridge.jsCall(VrmSceneCommand.Orbit(yawDeg)))
            return
        }
        // 未 ready：覆盖 pending 里上一个 orbit（同一笔拖动的中间态发了是抽搐），
        // 表情/嘴型仍按序排队。
        pending.removeAll { it is VrmSceneCommand.Orbit }
        pending += VrmSceneCommand.Orbit(yawDeg)
    }

    private fun queue(scene: VrmSceneCommand) {
        if (ready) send(VrmBridge.jsCall(scene)) else pending += scene
    }

    fun onPageReady() {
        ready = true
        val queued = pending.toList()
        pending.clear()
        queued.forEach { send(VrmBridge.jsCall(it)) }
    }
}
