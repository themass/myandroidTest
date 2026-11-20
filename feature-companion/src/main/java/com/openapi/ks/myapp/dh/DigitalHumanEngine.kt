package com.openapi.ks.myapp.dh

import android.content.Context
import android.view.View
import com.openapi.ks.myapp.avatar.AvatarRenderer

/**
 * Strategy：一个可切换的数字人后端。
 *
 * 继承 [AvatarRenderer]，通话通道仍只发 [com.openapi.ks.myapp.avatar.AvatarCommand]。
 * 需要 Android View（GL / TextureView）时实现 [createView]；Compose 引擎返回 null。
 */
interface DigitalHumanEngine : AvatarRenderer {
    val id: DhEngineId

    fun createView(context: Context): View? = null

    fun availability(context: Context): DhEngineAvailability

    /**
     * Duix 等需要 16kHz mono PCM16LE 驱动口型时覆写；默认空实现。
     * 出声仍由 LiveKit，SDK 侧应静音（只驱动）。
     */
    fun pushRemotePcm(pcm16leMono16k: ByteArray) {}

    /**
     * 「这一段说完了」：结束当前 PCM 推送段，让口型会话 flush 收尾、人物回到
     * 待机视频循环。PCM 驱动引擎（Duix）必须实现——推送段挂着不结束，待机循环
     * 的头部也带小幅摆动，静默态看起来就像她还在小声说话。默认空实现。
     */
    fun flushRemotePcm() {}

    /**
     * true = 这个引擎吃**真 PCM**（[pushRemotePcm]），[com.openapi.ks.myapp.dh.DhTestAudio]
     * 只推 PCM、不再合成 Mouth 指令（Duix 收到 Mouth 会合成假 PCM，和真 PCM 抢嘴型）。
     * false = 引擎听 Mouth 指令，rig 把 PCM 折算成幅度发过去（Simple / Live2D）。
     */
    val pcmDriven: Boolean get() = false
}
