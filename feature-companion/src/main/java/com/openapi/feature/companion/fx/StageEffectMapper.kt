package com.openapi.feature.companion.fx

import com.openapi.ks.myapp.companion.TransportConnection

/**
 * 触发源 → [StageEffect] 的唯一翻译点（纯函数，JVM 单测钉死）。
 *
 * 纪律照 [com.openapi.ks.myapp.avatar.AvatarEventMapper]：未知输入一律 null，
 * 绝不抛异常。特效是「锦上添花」，一个没见过的动作 id 绝不该把通话流程带崩。
 *
 * **不另起事件总线**（架构纪律 2026-11-23）：运行时所有触发都并进既有
 * `cmp.cue → AvatarJson → AvatarEventMapper → AvatarCommand.Gesture(id)` 词表，
 * 落到 [CompanionUiState.gesture]/[CompanionUiState.gestureSeq]；特效层只订阅
 * 这条既有 StateFlow 镜像，再用本 mapper 翻成 [StageEffect]。本对象只做词表翻译。
 *
 * 三条入口：
 * - [fromGesture]：服务端 cue 的 gesture id，以及本地并进同一词表的 id（托盘/接通）；
 * - [fromAction]：托盘动作 id → 特效（与 gesture 词表对齐；测试/直接映射用）；
 * - [onCallState]：传输层连接迁移是否该播接通特效（返回特效；运行时据此写入 gesture=`connected`）。
 */
object StageEffectMapper {

    /** 接通特效在 Gesture 词表里的 id——本地写入 uiState.gesture，与服务端 cue 同路。 */
    const val GESTURE_CONNECTED = "connected"

    /**
     * Gesture id → 特效。词表白名单：认得的翻成特效，认不得的（含空串）回 null。
     * 服务端与本地托盘/接通共用这一套，避免两套映射。
     */
    fun fromGesture(gestureId: String?): StageEffect? =
        gestureId?.trim()?.lowercase()?.let { id ->
            when (id) {
                "pat_head", "pathead", "head_pat" ->
                    play(FxAssets.PAT_HEAD, StageEffect.Anchor.HEAD)
                "wave", "waves", "greeting" ->
                    play(FxAssets.WAVE, StageEffect.Anchor.HAND)
                GESTURE_CONNECTED, "connect", "call_connected" ->
                    play(FxAssets.CONNECTED, StageEffect.Anchor.FULL)
                else -> null
            }
        }

    /** 本地托盘动作 id → 特效。置灰动作（turn/walk/nod）没有特效。 */
    fun fromAction(actionId: String?): StageEffect? =
        actionId?.trim()?.lowercase()?.let { id ->
            when (id) {
                "pat_head" -> play(FxAssets.PAT_HEAD, StageEffect.Anchor.HEAD)
                "wave" -> play(FxAssets.WAVE, StageEffect.Anchor.HAND)
                // sit_close 是距离语义，一期不给特效。
                else -> null
            }
        }

    /**
     * 连接状态迁移 → 特效。只有「首次真正接通」：prev 为 null 或 CONNECTING。
     * 重连恢复（RECONNECTING/DISCONNECTED → CONNECTED）不播——LiveKit 常态。
     * 运行时：非 null 则 [com.openapi.ks.myapp.companion.CompanionViewModel.emitStageGesture]
     * (`connected`)，并进既有 gestureSeq 流，不另起总线。
     */
    fun onCallState(prev: TransportConnection?, next: TransportConnection): StageEffect? {
        return when {
            next != TransportConnection.CONNECTED -> null
            prev == null || prev == TransportConnection.CONNECTING ->
                play(FxAssets.CONNECTED, StageEffect.Anchor.FULL, repeat = 1)
            else -> null
        }
    }

    private fun play(assetId: String, anchor: StageEffect.Anchor, repeat: Int = 1): StageEffect? =
        if (FxAssets.lookup(assetId) == null) null else StageEffect.Play(assetId, anchor, repeat)
}
