package com.openapi.feature.chat.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 顶层块切分器（整改清单 P1-12）：把 Markdown 按「顶层块」切开，
 * 让渲染缓存能复用已完成块，流式期每拍只重新解析最后（还在长）的那一块。
 *
 * 契约：
 * 1. concat(split(md)) == md —— 每块自带尾随空行分隔符，最后块的尾部分隔照原样保留；
 * 2. 增长单调 —— md 的任意前缀 p，split(p) 去掉最后一块后必须是 split(md) 的前缀
 *    （流式追加只会动最后一块，已完成的块不许变样，否则缓存全废）；
 * 3. 语义安全 —— 切点必须保证「各块单独渲染再拼回」和整篇渲染等价：
 *    围栏代码、松散列表（1. a / 空行 / 2. b）、缩进续行都不许切开。
 */
class MarkdownSegmenterTest {

    @Test
    fun wholeParagraphIsOneSegment() {
        assertEquals(listOf("第一行\n第二行"), MarkdownSegmenter.split("第一行\n第二行"))
    }

    @Test
    fun splitsAtBlankLineAndKeepsSeparator() {
        assertEquals(listOf("段落一\n\n", "段落二"), MarkdownSegmenter.split("段落一\n\n段落二"))
        // 连续多个空行原样留在分隔里，拼回去必须一字不差
        assertEquals(listOf("a\n\n\n\n", "b"), MarkdownSegmenter.split("a\n\n\n\nb"))
    }

    @Test
    fun trailingBlankLinesStayOnLastSegment() {
        assertEquals(listOf("a\n\n"), MarkdownSegmenter.split("a\n\n"))
    }

    @Test
    fun concatAlwaysReproducesInput() {
        for (md in listOf(
            "", "a", "a\n", "a\n\nb", "# H\n\ntext\n\n- x\n- y\n",
            "a\n```k\nx\n\ny\n```\n\nz\n", "1. a\n\n2. b\n\npara\n", "para\n\n    indented\n",
        )) {
            assertEquals("concat 必须还原原文: <$md>", md, MarkdownSegmenter.split(md).joinToString(""))
        }
    }

    @Test
    fun blankLinesInsideFenceStayOneSegment() {
        val md = "```\n代码\n\n还有代码\n```\n\n结束"
        assertEquals(listOf("```\n代码\n\n还有代码\n```\n\n", "结束"), MarkdownSegmenter.split(md))
    }

    @Test
    fun unclosedFenceSwallowsTheRest() {
        // 流式中围栏还没闭合：后面全是同一块，不许切开
        val md = "正文\n\n```\nprint(1)\n\nprint(2)\n"
        assertEquals(listOf("正文\n\n", "```\nprint(1)\n\nprint(2)\n"), MarkdownSegmenter.split(md))
    }

    @Test
    fun looseListStaysOneSegment() {
        // 松散有序列表：切开会让 "2." 重排成 "1."，必须留在同一块
        assertEquals(listOf("1. 甲\n\n2. 乙"), MarkdownSegmenter.split("1. 甲\n\n2. 乙"))
        // 同种弹头的松散无序列表同理
        assertEquals(listOf("- 甲\n\n- 乙"), MarkdownSegmenter.split("- 甲\n\n- 乙"))
    }

    @Test
    fun plainParagraphLazilyContinuesTheList() {
        // CommonMark 的坑（markdown-it/GFM 一致）：空行后的普通文字段会被 lazy 续行吸进列表，
        // 所以这个空行处切开【会改变渲染】（<p> 从 <li> 里跑到外面）——必须并进列表段。
        assertEquals(listOf("1. 甲\n\n2. 乙\n\n段落"), MarkdownSegmenter.split("1. 甲\n\n2. 乙\n\n段落"))
        assertEquals(listOf("列表\n- 项\n\n段落\n"), MarkdownSegmenter.split("列表\n- 项\n\n段落\n"))
        // 真正的块起始（标题/围栏/引用）才结束列表：这里必须切开
        assertEquals(
            listOf("1. 甲\n\n2. 乙\n\n", "## 标题"),
            MarkdownSegmenter.split("1. 甲\n\n2. 乙\n\n## 标题"),
        )
    }

    @Test
    fun indentedContinuationStaysWithPreviousBlock() {
        // 4 空格缩进是列表内容；空行后的普通段又被 lazy 吸回——整条留在一块
        val md = "1. 项\n\n    续行\n\n段落"
        assertEquals(listOf(md), MarkdownSegmenter.split(md))
        // 但段落后的缩进块是独立代码块，要切开
        assertEquals(listOf("段落\n\n", "    代码\n"), MarkdownSegmenter.split("段落\n\n    代码\n"))
    }

    @Test
    fun blankLineInsideIndentedCodeStaysOneSegment() {
        // CommonMark：缩进代码块内部可以有空行，只要后面还接着缩进行，就还是同一个代码块。
        // 在这里切开会把一块代码变成两块，渲染结果不一样——必须并进同一段。
        val md = "    a\n\n    b\n"
        assertEquals(listOf(md), MarkdownSegmenter.split(md))
        // 空行后不缩进了才是普通段落：这里要切开
        assertEquals(listOf("    a\n\n", "b\n"), MarkdownSegmenter.split("    a\n\nb\n"))
    }

    @Test
    fun danglingEmptyMarkerStaysWithPreviousBlock() {
        // 光杆块标记（流式刚打出 `-`、`#`、`2.` 的第一个字符）：它是否被解析成空块
        // （渲染吞掉）取决于前文有没有段落（commonmark-java 列表打断规则，实测），
        // 单独成段却按文本保留——切开拼接就对不上，必须并进上一段；
        // 等它长出头（`- 项`）就是普通块，照常切。
        assertEquals(listOf("段落\n\n-"), MarkdownSegmenter.split("段落\n\n-"))
        assertEquals(listOf("段落\n\n#"), MarkdownSegmenter.split("段落\n\n#"))
        assertEquals(listOf("段落\n\n1."), MarkdownSegmenter.split("段落\n\n1."))
        assertEquals(listOf("段落\n\n-\n"), MarkdownSegmenter.split("段落\n\n-\n"))
        assertEquals(listOf("段落\n\n-\n\n"), MarkdownSegmenter.split("段落\n\n-\n\n"))
        // 长出头之后正常切
        assertEquals(listOf("段落\n\n", "- 项\n"), MarkdownSegmenter.split("段落\n\n- 项\n"))
        // 光杆标记后面的标题也并进同一段：它在前、在后都会被解析成空块+孤立空行，
        // 切开拼接和整篇渲染对不上（实测 whole=「段落\n\n\n标题」，切开怎么拼都差一个换行）
        assertEquals(listOf("段落\n\n-\n\n## 标题"), MarkdownSegmenter.split("段落\n\n-\n\n## 标题"))
    }

    @Test
    fun growthOnlyEverTouchesTheLastSegment() {
        val full = "# 标题\n\n段落一，比较长。\n\n- 甲\n- 乙\n\n```\nval x = 1\n\nval y = 2\n```\n\n1. 一\n\n2. 二\n\n结尾段。\n"
        val fullSegs = MarkdownSegmenter.split(full)
        check(fullSegs.size >= 5)
        for (n in 1..full.length) {
            val p = full.substring(0, n)
            val grown = MarkdownSegmenter.split(p)
            val stable = grown.dropLast(1)
            assertEquals(
                "前缀 <$p> 的已完成块必须还是全文块序列的前缀",
                fullSegs.take(stable.size),
                stable,
            )
        }
    }
}
