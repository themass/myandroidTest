package com.openapi.feature.photosolve.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 结果页/历史页排版相关纯函数的单测（A37：字号收口的同时把「靠肉眼验」的规则锁进测试）。
 *
 * - [summaryOf]：思考过程折叠时的摘要（第一句、限长），决定折叠卡片的高度是否稳定；
 * - [formatTime]：历史列表/详情共用时间格式化，0 表示「没有时间戳」。
 */
class ResultSummaryTest {

    @Test
    fun firstChineseSentenceWins() {
        assertEquals(
            "先审题：这是一元二次方程。",
            summaryOf("先审题：这是一元二次方程。然后用求根公式解。"),
        )
    }

    @Test
    fun semicolonAlsoEndsSentence() {
        assertEquals("先把已知条件整理清楚；", summaryOf("先把已知条件整理清楚；再列方程求解。"))
    }

    @Test
    fun decimalPointIsNotSentenceEnd() {
        // 旧实现把 `x=1.5` 里的半角点当句末，摘要被截成「设 x=1.」
        assertEquals(
            "设 x=1.5，则另一边为 2.5。",
            summaryOf("设 x=1.5，则另一边为 2.5。"),
        )
    }

    @Test
    fun standaloneDotStillEndsSentence() {
        assertEquals("Step one is done.", summaryOf("Step one is done. Step two follows."))
    }

    @Test
    fun shortPrefixBeforeCutIsKeptWhole() {
        // cut <= 8：句子太短不值得只取第一句，保留整行
        assertEquals("很短。后面还有一大段说明文字。", summaryOf("很短。后面还有一大段说明文字。"))
    }

    @Test
    fun blankLinesAreSkipped() {
        // 前导空行跳过；首句 >8 字时只取首句（同行后面的内容不要）
        assertEquals("第二行才是正文内容。", summaryOf("\n\n第二行才是正文内容。第三句不看。"))
    }

    @Test
    fun noPunctuationFallsBackToFirstLine() {
        assertEquals("没有任何句读的一行长思考", summaryOf("没有任何句读的一行长思考"))
    }

    @Test
    fun overlongFirstLineIsTruncatedWithEllipsis() {
        val long = "字".repeat(100)
        val out = summaryOf(long)
        assertEquals("字".repeat(80) + "…", out)
    }

    @Test
    fun blankInputFallsBackToTrimmedTake() {
        assertEquals("", summaryOf("   "))
        assertEquals("裸文本兜底", summaryOf("　\n裸文本兜底\n第二行"))
    }

    @Test
    fun zeroTimestampRendersEmpty() {
        assertEquals("", formatTime(0L))
        assertEquals("", formatTime(-1L))
    }

    @Test
    fun positiveTimestampHasTimeFormat() {
        // MM-dd HH:mm —— 不钉死时区，只锁形状：列表与详情同一格式
        val out = formatTime(1_764_000_000_000L)
        assert(out.matches(Regex("""\d{2}-\d{2} \d{2}:\d{2}"""))) { "unexpected: $out" }
    }
}
