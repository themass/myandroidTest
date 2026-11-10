package com.openapi.chat.presentation

/**
 * 流式回复的打字机节奏：每一拍显示多少字、怎么切不把 emoji 拆开。
 * 从 ChatViewModel 拆出来，便于单独测试。
 */
internal object RevealPacer {

    /** 本拍释放多少字：积压越多越快（上限 48），流结束后加速排空。 */
    fun revealCount(pending: Int, ended: Boolean): Int {
        val base = (pending / 8).coerceIn(1, 48)
        return if (ended) maxOf(base, pending / 3) else base
    }

    /** 不要把一个 emoji 等代理对从中间切开。 */
    fun safeCut(text: CharSequence, wanted: Int): Int {
        var cut = wanted.coerceAtMost(text.length)
        if (cut in 1 until text.length && Character.isHighSurrogate(text[cut - 1])) cut += 1
        return cut
    }
}
