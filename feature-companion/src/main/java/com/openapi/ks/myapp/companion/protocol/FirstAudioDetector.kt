package com.openapi.ks.myapp.companion.protocol

/**
 * 「用户真正听到声音」的判据：远端 PCM 流里第一个能量帧。
 *
 * `remote_pcm_first` 只是 sink 收到第一个回调——LiveKit 会先送引导静音（服务端
 * `tts first … peak=0.0024` 的静音形态与此对应），拿它当出声时刻在两轮实测里都偏早。
 * F16 测量要 ④ 段（TTS 首帧→设备首个非零 PCM），没有这条判据就只能框进 10s 汇总窗口。
 *
 * 纯逻辑、无时钟、每帧 O(1) 不分配——挂在口型 sink 同一路径上跑。
 */
class FirstAudioDetector(private val rmsThreshold: Double) {
    @Volatile
    private var fired = false

    /** 本帧是首个过阈值的能量帧时返回 true 并闭闸；之后的所有帧都返回 false。 */
    fun onFrame(rms: Double): Boolean {
        if (fired) return false
        if (rms <= rmsThreshold) return false
        fired = true
        return true
    }

    /** 新通话/新 attempt 复位（与 firstRemotePcmLogged 同一处调用）。 */
    fun reset() {
        fired = false
    }
}
