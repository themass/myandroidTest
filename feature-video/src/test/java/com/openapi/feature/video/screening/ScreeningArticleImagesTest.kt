package com.openapi.feature.video.screening

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A40「文章页图片经常没有、快速进出还闪退」（用户 2026-11-26）。
 *
 * 取证三条，全在正文这条链上：
 * ① JS 被 1312b454 整个关掉——Discuz 帖子的图是懒加载（`data-src` 里藏着真地址，
 *   `src` 是占位 blank），旧 TextItemsFragment 开着 JS 才显形；JS 一关只剩占位图。
 * ② `loadDataWithBaseURL(null, …)`：正文里的相对地址（`/data/…`、`static/…`）
 *   没有 base 根本解析不出来，Chromium 直接丢请求。
 * ③ 帖子绝对地址是 `http://`，而 network_security_config 全局禁明文——明文图片
 *   请求被系统掐掉。
 * 图片归一化成纯函数先测；①②③与退出动效的接线用结构钉子钉（WebView 不在
 * JVM 单测里跑，这三处漏接哪一条，图片都回不来）。
 */
class ScreeningArticleImagesTest {

    // ---- ③ 图片地址归一化（纯函数）----

    @Test
    fun `懒加载图 data-src 顶替成 src，src 里的占位不再当真`() {
        val out = normalizeBodyImages("""<img src="static/image/common/blank.gif" data-src="https://cdn.x/a.jpg" width="500">""")
        assertTrue("src 必须换成真地址：$out", out.contains("""src="https://cdn.x/a.jpg""""))
        assertTrue("其余属性原样保留：$out", out.contains("""width="500""""))
    }

    @Test
    fun `没有 data-src 的图原样不动`() {
        val tag = """<img src="https://cdn.x/b.jpg">"""
        assertTrue(normalizeBodyImages(tag) == tag)
    }

    @Test
    fun `图片明文 http 升级为 https（全局禁明文，不升级就是丢图）`() {
        val out = normalizeBodyImages("""<img src="http://img.x/c.jpg">""")
        assertTrue("应升级 scheme：$out", out.contains("""src="https://img.x/c.jpg""""))
    }

    @Test
    fun `data-src 是明文时，顶替后也一并升级 https`() {
        val out = normalizeBodyImages("""<img src="blank.gif" data-src="http://img.x/d.jpg">""")
        assertTrue(out.contains("""src="https://img.x/d.jpg""""))
    }

    @Test
    fun `只动图片标签，帖子正文里的普通链接不被改写`() {
        val body = """<a href="http://forum.x/t1">帖子</a><p>文字 http://forum.x</p>"""
        assertTrue("非 img 标签原样：$body", normalizeBodyImages(body) == body)
    }

    @Test
    fun `整页包装走的是归一化后的图片（链路不脱节）`() {
        val page = articleBodyHtml("""<img src="blank.gif" data-src="http://img.x/e.jpg">""")
        assertTrue("包装页必须带升级后的真地址：$page", page.contains("""src="https://img.x/e.jpg""""))
        // 懒加载护栏（loading=lazy）仍要在——归一化不能把它挤掉。
        assertTrue(page.contains("loading=\"lazy\""))
    }

    // ---- ①②③与退出释放的接线（结构钉子）----

    @Test
    fun `正文 WebView 必须开回 JS（懒加载脚本靠它），文件访问仍禁`() {
        val src = File("src/main/java/com/openapi/feature/video/screening/ScreeningText.kt").readText()
        val body = bodyOf(src, "private fun TextHtmlBody")
        assertTrue("JS 必须开（1312b454 一刀切关掉把懒加载图也关了）：A40",
            body.contains("javaScriptEnabled = true"))
        assertTrue("allowFileAccess 保持关（正文不需要读本地文件）",
            body.contains("allowFileAccess = false"))
    }

    @Test
    fun `正文加载必须带站点根 baseUrl，相对图片才解析得出`() {
        val src = File("src/main/java/com/openapi/feature/video/screening/ScreeningText.kt").readText()
        val body = bodyOf(src, "private fun TextHtmlBody")
        assertFalse("loadDataWithBaseURL 不许再传 null base",
            Regex("""loadDataWithBaseURL\(\s*null,""").containsMatchIn(body))
        assertTrue("要传站点根（Constants.BASE_IP 拼的 https://…/）",
            body.contains("TEXT_BODY_BASE_URL"))
    }

    @Test
    fun `离开阅览层立即退场，旧 WebView 不等动效就释放`() {
        val src = File("src/main/java/com/openapi/feature/video/screening/ScreeningText.kt").readText()
        // AnimatedContent 的退场内容会拖到动效结束才 onRelease——阅览层里是 WebView，
        // 快速进出时两个 Chromium 渲染器叠着解码大图，正是闪退现场（A40）。
        assertTrue("退离阅览层（initialState==2）必须换零时长退场那条转场",
            Regex("""initialState == 2""").containsMatchIn(src))
        assertTrue("ScreeningText 要走 screenSlideNoExit",
            src.contains("screenSlideNoExit("))
        // 零时长退场本体在 ScreeningMotion（别在调用点复制一份入场动效）。
        val motion = File("src/main/java/com/openapi/feature/video/screening/ScreeningMotion.kt").readText()
        assertTrue("screenSlideNoExit 必须让旧内容零时长退场",
            Regex("""initialContentExit\s*=\s*ExitTransition\.None""").containsMatchIn(motion))
    }

    private fun bodyOf(text: String, fn: String): String {
        val start = text.indexOf(fn)
        assertTrue("找不到 $fn", start >= 0)
        val begin = text.indexOf('{', text.indexOf(')', start))
        assertTrue("$fn 没有函数体", begin >= 0)
        var depth = 0
        var i = begin
        while (i < text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(begin, i + 1)
                }
            }
            i++
        }
        return text.substring(begin)
    }
}
