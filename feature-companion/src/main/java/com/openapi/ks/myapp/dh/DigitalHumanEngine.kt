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
}
