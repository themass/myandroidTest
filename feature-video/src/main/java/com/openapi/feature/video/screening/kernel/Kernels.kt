package com.openapi.feature.video.screening.kernel

import android.content.Context
import android.net.Uri
import android.view.Surface
import java.io.File

/**
 * 播放内核的统一外壳。工程里已经有三套可用的内核（见 [com.openapi.feature.video.PlaybackEngineHelper]
 * 与 `PlaybackEnginePrefs`）：Media3 Exo、IJK + ex_so 的 FFmpeg、系统 MediaPlayer。
 *
 * 这里不引入新的解码器，只是把三者收进同一个 `open / play / pause / seek / speed` 接口，
 * 让同一个画面槽位能在播放中途换内核。[pref] 与 `PlaybackEnginePrefs` 的常量保持一致，
 * 所以在预览页里换内核会同步写回全 App 的偏好，别处打开视频也走同一个选择。
 */
internal enum class Kernel(val title: String, val note: String, val pref: Int) {
    EXO("Exo", "Media3 · 兼容性最好", 0),
    IJK("IJK", "FFmpeg 扩展 · 冷门格式也能播", 1),
    SYSTEM("系统", "系统解码器 · 最省电", 3),
    ;

    companion object {
        fun ofPref(value: Int): Kernel = entries.firstOrNull { it.pref == value } ?: EXO
    }
}

internal interface KernelPlayer {
    /** 绑定输出画面。两套内核各占一格画面槽，交接时才能同时有画面。 */
    fun attach(surface: Surface)
    /** [file] 非空时优先用文件路径（asset 已落盘）；否则按 [uri] 交给内核自己解析。 */
    fun open(uri: Uri, file: File?, startMs: Long)
    fun play()
    fun pause()
    fun seekTo(ms: Long)
    fun setSpeed(speed: Float)
    fun setVolume(volume: Float)
    fun release()

    val position: Long
    val duration: Long
    val playing: Boolean
    /**
     * 正在缓冲（seek 后重载分片也算）。seek 落点前内核的 position 会短暂报 0，
     * UI 靠它决定「钉住不动」而不是拿 0 当真值（真机：拖进度条弹回 0:00）。
     */
    val buffering: Boolean
    /** 已经准备完、可以接受 seek/起播。准备中为 false，[playing] 此时一定是 false。 */
    val ready: Boolean
    /** 宽高比（含像素比），未知时返回 0。 */
    val ratio: Float
}

internal interface KernelCallbacks {
    /** 解码器已就绪，可以 seek 和起播。 */
    fun onReady(player: KernelPlayer)
    /** 第一帧已经上屏：交接就在这一刻发生，用户看不到空窗。 */
    fun onFirstFrame(player: KernelPlayer)
    fun onEnded(player: KernelPlayer)
    fun onFailure(player: KernelPlayer, reason: String)
}

internal fun createKernel(kind: Kernel, context: Context, callbacks: KernelCallbacks): KernelPlayer =
    when (kind) {
        Kernel.EXO -> ExoKernel(context, callbacks)
        Kernel.IJK -> IjkKernel(context, callbacks)
        Kernel.SYSTEM -> SystemKernel(context, callbacks)
    }
