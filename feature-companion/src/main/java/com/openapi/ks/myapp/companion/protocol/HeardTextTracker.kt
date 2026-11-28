package com.openapi.ks.myapp.companion.protocol

/**
 * 字幕流的端侧镜像（C2 的数据源）。
 *
 * `lk.transcription` 的 delta 逐块在这里攒成「这条回复送到端上的全部文本」。
 * 打断瞬间 [snapshot] 喂给 [HeardPrefix.slice]，按播放器进度切出 `heard_text`。
 * 回合边界（[onTurnDone]）与打断（[onInterrupted]）都清零——镜像只服务当前一轮，
 * 不留上一轮的字去切下一条前缀。
 *
 * 为什么在传输层镜像而不是读 ViewModel 的气泡：气泡是 UI 状态，传输层不该反向依赖它；
 * 而且气泡会被本地截断改写，镜像要保持的是「服务端送来了什么」这个原始事实。
 */
class HeardTextTracker(private val maxChars: Int = 4_000) {

    private val sb = StringBuilder()

    fun onDelta(delta: String) {
        sb.append(delta)
        if (sb.length > maxChars) sb.setLength(maxChars)
    }

    fun snapshot(): String = sb.toString()

    fun onTurnDone() {
        sb.setLength(0)
    }

    fun onInterrupted() {
        sb.setLength(0)
    }
}
