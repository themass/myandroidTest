package com.openapi.feature.video.subtitle.local

import java.io.IOException
import java.io.Reader

/**
 * 新播放页的字幕状态：一条本地轨 + 开关 + 延时。
 *
 * 解析仍复用已单测过的 [LocalSubtitleTrack]（SRT / WebVTT 的纯文本子集）；
 * 这里只负责“当前是否显示、偏了多少”，显示位置一律来自媒体播放位置，不用墙钟。
 */
class LocalSubtitleStore {

    @Volatile
    var enabled: Boolean = true

    /** 正数表示字幕晚出现（与设计稿的 +500 ms 一致）。 */
    @Volatile
    var delayMs: Int = 0

    private var track: LocalSubtitleTrack? = null

    /** 已导入文件的显示名，用于字幕面板回显；没有导入时为 null。 */
    @Volatile
    var displayName: String? = null
        private set

    val hasTrack: Boolean
        get() = track != null

    @Throws(IOException::class)
    fun load(reader: Reader, name: String?) {
        val parsed = LocalSubtitleTrack.read(reader)
        track = parsed
        displayName = name
        enabled = true
        delayMs = 0
    }

    fun clear() {
        track = null
        displayName = null
        delayMs = 0
    }

    fun shiftDelay(deltaMs: Int) {
        delayMs = (delayMs + deltaMs).coerceIn(-MAX_DELAY_MS, MAX_DELAY_MS)
    }

    /** 当前播放位置应显示的字幕，没有内容时返回空串。 */
    fun textAt(positionMs: Long): String {
        val current = track ?: return ""
        if (!enabled) return ""
        return current.at(positionMs, delayMs)
    }

    private companion object {
        const val MAX_DELAY_MS = 60_000
    }
}
