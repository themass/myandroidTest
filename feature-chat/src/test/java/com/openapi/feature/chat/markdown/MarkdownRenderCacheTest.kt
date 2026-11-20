package com.openapi.feature.chat.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import io.noties.markwon.Markwon

/**
 * 分段渲染缓存（整改清单 P1-12）：
 * - 正确性：任意前缀渲染出来的文字必须和「整篇一次渲染」逐字相同（含带链接时 URLSpan 不丢）；
 * - 性能契约：每个 tick 只允许重新解析「还没完成」的段——被解析过的原始串必须始终是
 *   单个顶层段，任何一次都不许解析「含两个块的前缀」（旧写法的 O(n²) 就长这样）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MarkdownRenderCacheTest {

    /** 记录每次 toMarkdown 入参的代理：性能断言不许在生产类里开测试后门。 */
    private class RecordingMarkwon(private val d: Markwon) : Markwon() {
        val parsed = mutableListOf<String>()
        override fun parse(input: String): org.commonmark.node.Node = d.parse(input)
        override fun render(node: org.commonmark.node.Node): android.text.Spanned = d.render(node)
        override fun toMarkdown(input: String): android.text.Spanned {
            parsed += input
            return d.toMarkdown(input)
        }
        override fun setMarkdown(textView: android.widget.TextView, markdown: String) = d.setMarkdown(textView, markdown)
        override fun setParsedMarkdown(textView: android.widget.TextView, markdown: android.text.Spanned) =
            d.setParsedMarkdown(textView, markdown)
        override fun hasPlugin(plugin: Class<out io.noties.markwon.MarkwonPlugin>) = d.hasPlugin(plugin)
        override fun <P : io.noties.markwon.MarkwonPlugin> getPlugin(plugin: Class<P>): P? = d.getPlugin(plugin)
        override fun <P : io.noties.markwon.MarkwonPlugin> requirePlugin(plugin: Class<P>): P = d.requirePlugin(plugin)
        override fun getPlugins(): List<out io.noties.markwon.MarkwonPlugin> = d.plugins
        override fun configuration(): io.noties.markwon.MarkwonConfiguration = d.configuration()
    }

    private fun markwon(): RecordingMarkwon =
        RecordingMarkwon(Markwon.create(org.robolectric.RuntimeEnvironment.getApplication()))

    /** 一条典型的流式长回答：标题、段落、松散列表、围栏代码、链接。 */
    private val doc = """
        # 标题

        第一段，比较长的一段中文文字，中间有**加粗**和~~删除~~。

        - 甲项
        - 乙项

        1. 一

        2. 二

        ```kotlin
        val x = 1

        val y = 2
        ```

        见[链接](https://example.com)说明。

        结尾段。
    """.trimIndent() + "\n"

    @Test
    fun everyPrefixRendersIdenticalToWholeRender() {
        val m = markwon()
        val cache = MarkdownRenderCache()
        for (n in 0..doc.length) {
            val p = doc.substring(0, n)
            val cached = cache.render(m, p)
            val whole = m.toMarkdown(FormulaInputNormalizer.forRender(p))
            assertEquals("前缀 n=$n 渲染不一致", whole.toString(), cached.toString())
        }
    }

    @Test
    fun linkSpansSurviveSegmentConcat() {
        val m = markwon()
        val cache = MarkdownRenderCache()
        val spanned = cache.render(m, doc)
        val whole = m.toMarkdown(FormulaInputNormalizer.forRender(doc))
        val nSpanned = (spanned as android.text.Spanned).getSpans(0, spanned.length, android.text.style.URLSpan::class.java).size
        val nWhole = whole.getSpans(0, whole.length, android.text.style.URLSpan::class.java).size
        assertEquals("拼接后链接 span 数量必须和整篇渲染一致", nWhole, nSpanned)
        assertEquals(1, nSpanned)
    }

    @Test
    fun onlyStandaloneSegmentsAreEverParsed() {
        val m = markwon()
        val cache = MarkdownRenderCache()
        for (n in 0..doc.length step 3) cache.render(m, doc.substring(0, n))
        // 每次实际解析的原始串都必须是「单个顶层段」：解析过多段文本 = 又退回了整篇重解析
        assertTrue("至少要解析过一些段", m.parsed.isNotEmpty())
        for (raw in m.parsed) {
            assertEquals("解析了多段文本: <${raw.replace("\n", "\\n")}>", 1, MarkdownSegmenter.split(raw).size)
        }
    }

    @Test
    fun sameRawTwiceReusesResultWithoutParsing() {
        val m = markwon()
        val cache = MarkdownRenderCache()
        val first = cache.render(m, doc)
        val parses = m.parsed.size
        val again = cache.render(m, doc)
        assertSame("同一文本重复重组必须原样返回（Compose 重组不重渲）", first, again)
        assertEquals("没变化就不该有任何解析", parses, m.parsed.size)
    }

    @Test
    fun growingTailOnlyReparsesLastSegments() {
        val m = markwon()
        val cache = MarkdownRenderCache()
        cache.render(m, doc)
        val before = m.parsed.size
        // 追加一整段新内容：只许重新解析最后两段（旧尾段补上分隔符换行 + 新段各一次），
        // 更靠前的已完成段一次都不许多碰。原文不剪尾随换行做 key，是未闭合围栏
        // （尾随空行属于代码内容）逼出来的；流式逐拍渲染时段落分隔成型的瞬间
        // 本来就在渲染尾段，摊下来仍是每拍一段。
        cache.render(m, doc + "\n新段落，来了。\n")
        assertTrue("追加一段最多重渲最后两段: ${m.parsed.size - before}", m.parsed.size - before <= 2)
    }

    @Test
    fun segmentThatRendersEmptyDoesNotAddSeparator() {
        val m = markwon()
        val cache = MarkdownRenderCache()
        // `-` 行带真换行，单独解析与整篇一致：解析成空列表项、渲染为空。
        // 拼接时必须把它和它的空行分隔一起跳过，否则多出一段空行。
        val md = "段落\n\n-\n\n## 标题"
        val whole = m.toMarkdown(FormulaInputNormalizer.forRender(md))
        assertEquals(whole.toString(), cache.render(m, md).toString())
    }

    @Test
    fun emptyAndBlankInputAreEmpty() {
        val m = markwon()
        val cache = MarkdownRenderCache()
        assertEquals("", cache.render(m, "").toString())
        assertEquals("", cache.render(m, "\n\n").toString())
    }
}
