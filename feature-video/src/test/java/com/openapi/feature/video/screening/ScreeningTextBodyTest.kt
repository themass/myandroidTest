package com.openapi.feature.video.screening

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 正文 HTML 包装的钉子：服务端回的是论坛帖子片段（Discuz `t_msgfontfix` 一类），
 * 它是按白底黑字写的——放映场整屏深色底上，WebView 透明就等于黑字压黑底，
 * 什么都看不见（真机 2026-11-23 用户点名）。旧文章页（[com.openapi.common.ui.widget.MyWebView]
 * .loadHtmlBody + [com.openapi.feature.content.text.TextItemsFragment]）的观感是浅色纸面：
 * 包装必须自己把纸面、字色、viewport、图片自适应补齐，帖子内容原样保留。
 */
class ScreeningTextBodyTest {

    @Test
    fun `正文包装必须自带浅色纸面和深色字色`() {
        val wrapped = articleBodyHtml("<div class=\"t_msgfontfix\">正文内容</div>")
        // 真机黑底黑字的根因就缺这两样：显式浅色底 + 显式字色
        assertTrue("缺浅色底", wrapped.contains("background:#FFFDF8"))
        assertTrue("缺字色", wrapped.contains("color:#1A1A1A"))
    }

    @Test
    fun `包装对齐旧 loadHtmlBody：viewport 与图片自适应，帖子内容原样在内`() {
        val wrapped = articleBodyHtml("<div class=\"t_msgfontfix\">正文内容</div>")
        assertTrue("缺 viewport", wrapped.contains("viewport"))
        assertTrue("缺图片自适应", wrapped.contains("max-width:100%"))
        assertTrue("丢内容", wrapped.contains("正文内容"))
    }
}
