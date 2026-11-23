package com.openapi.feature.chat.markdown

/**
 * 将 Markdown 拆成「已稳定的前缀」与「尾部待重解析段」。
 * 稳定段 = 除最后一段外的所有段落（以连续空行分隔）。
 * 流式时只有尾部在变，前缀可缓存 Markwon 结果。
 */
internal object MarkdownStableTail {

    data class Split(val stable: String, val tail: String)

    fun split(normalizedMarkdown: String): Split {
        if (normalizedMarkdown.isEmpty()) return Split("", "")
        val parts = normalizedMarkdown.split("\n\n")
        if (parts.size == 1) return Split("", normalizedMarkdown)
        val stable = parts.dropLast(1).joinToString("\n\n") + "\n\n"
        val tail = parts.last()
        return Split(stable, tail)
    }
}
