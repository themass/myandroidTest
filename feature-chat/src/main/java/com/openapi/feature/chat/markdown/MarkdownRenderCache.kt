package com.openapi.feature.chat.markdown

import android.text.SpannableStringBuilder
import android.text.Spanned
import io.noties.markwon.Markwon

/**
 * 分段渲染缓存（整改清单 P1-12）：流式回答每拍只对「还在长的最后一段」做 Markdown 解析，
 * 已完成的顶层块复用上次渲染好的 Spanned——把 `MarkdownText` 每次重组全文重解析的
 * O(n²) 变成每拍 O(尾段)。
 *
 * 正确性由 [MarkdownRenderCacheTest] 钉住：任意前缀渲染出的文字与整篇一次渲染逐字相同
 * （Robolectric + 真 Markwon 全前缀比对），且任何一次解析的输入都不许多于一个顶层块。
 * 唯一的已知偏差（「光杆标记行紧接标题/围栏」时少一个空行）也钉在测试里并写明原因。
 *
 * 拼接规则「非空渲染段之间补 `"\n\n"`」对「每段 = 单个顶层块」成立（实测探针 + 全前缀比对）；
 * 段内含空渲染块（空列表项）时 Markwon 的分隔不对称，切段器用 DANGLING 合并规则尽量
 * 把这种行并进前一段，让它尽量不出现在段间。
 *
 * 用法：每个 [com.openapi.feature.chat.presentation.ui.MarkdownText] 实例 `remember` 一个，
 * 配对它实际用来渲染的那个 Markwon 单例（[ChatMarkwon]）。同一个文本重复请求直接返回
 * 同一个 Spanned——Compose 重组不再触发 setText。
 */
internal class MarkdownRenderCache {

    /** 各段去掉尾部空行后的内容（key）与渲染结果，一一对应；key 为空的段渲染必为空。 */
    private var keys = emptyList<String>()
    private var parts = emptyList<CharSequence>()
    private var lastMd: String? = null
    private var lastResult: Spanned? = null

    fun render(markwon: Markwon, markdown: String): Spanned {
        // Compose 重组（滚动、相邻 item 状态变化）文本没变：直接给回上次的结果
        if (markdown == lastMd) return lastResult!!

        val segs = MarkdownSegmenter.split(FormulaInputNormalizer.forRender(markdown))
        // key = 段原文。不做 trimEnd('\n') 归一：未闭合围栏会把尾随空行吃进代码内容
        // （实测 ```k\nv\n\n 渲染出尾随空行，```k\nv\n 没有），剪掉就把两种输入混成同一个 key。
        // 代价：上一段刚「完结」（尾随分隔符从无到有）会多渲一次，仍是每拍 O(1)。
        // 已完成段的原文由切段器的增长单调契约保证字节稳定，永远命中复用。
        var i = 0
        while (i < keys.size && i < segs.size && keys[i] == segs[i]) i++

        val newKeys = ArrayList<String>(segs.size)
        val newParts = ArrayList<CharSequence>(segs.size)
        newKeys += keys.take(i)
        newParts += parts.take(i)
        for (j in i until segs.size) {
            // 必须解析 segmenter 给的原始段：CommonMark 的行尾参与块判定，
            // 任何“修饰”（补换行、剪空行）都会让半截回答和整篇渲染对不上。
            newKeys += segs[j]
            newParts += if (segs[j].isBlank()) "" else markwon.toMarkdown(segs[j])
        }
        keys = newKeys
        parts = newParts

        // 拼接 = 非空渲染段之间补空行分隔；空段（纯空行）连分隔一起跳过
        val sb = SpannableStringBuilder()
        var first = true
        for (part in parts) {
            if (part.isEmpty()) continue
            if (!first) sb.append("\n\n")
            first = false
            sb.append(part) // append Spanned 会连 span 一起带过来（链接等）
        }
        lastMd = markdown
        lastResult = sb
        return sb
    }
}
