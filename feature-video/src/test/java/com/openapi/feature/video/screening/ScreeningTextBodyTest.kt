package com.openapi.feature.video.screening

import org.junit.Assert.assertFalse
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

    @Test
    fun `大图护栏：超长图限高、禁止页面缩放抢手势`() {
        val wrapped = articleBodyHtml("<img src=\"x\">")
        // 长截图/连图不加高上限会把整页撑成巨幅滚动面（大量图卡顿、内存峰值高的根源之一）。
        assertTrue("缺最大高度", wrapped.contains("max-height"))
        // 缩放关死（旧口径）：正文页的横向手势留给「右滑关闭文章页」，不给 pinch-zoom 抢。
        assertTrue("禁缩放", wrapped.contains("user-scalable=no"))
    }

    @Test
    fun `连图懒加载：img 一律补 loading=lazy，已有的不重复补`() {
        val wrapped = articleBodyHtml("<img src=\"a\"><IMG loading=\"eager\" src=\"b\"><img src=\"c\" loading=\"lazy\">")
        assertTrue("没补懒加载", wrapped.contains("<img loading=\"lazy\" src=\"a\">"))
        assertTrue("改了已有 loading", wrapped.contains("loading=\"eager\""))
        assertTrue("重复补 loading", !wrapped.contains("loading=\"lazy\" loading="))
    }

    @Test
    fun `文章页右滑关闭判定：横移够远且够快才算，竖滑不算`() {
        val minD = 200f; val minV = 600f
        // 干脆利落的右滑
        assertTrue(articleSwipeCloses(dx = 300f, velocityPxPerS = 1200f, minDistancePx = minD, minVelocityPx = minV))
        // 距离不够（误触轻扫）
        assertFalse(articleSwipeCloses(dx = 50f, velocityPxPerS = 1200f, minDistancePx = minD, minVelocityPx = minV))
        // 慢蹭到位置（用户在选文字）
        assertFalse(articleSwipeCloses(dx = 300f, velocityPxPerS = 100f, minDistancePx = minD, minVelocityPx = minV))
        // 左滑永远不关闭（dx 为负）
        assertFalse(articleSwipeCloses(dx = -400f, velocityPxPerS = -1500f, minDistancePx = minD, minVelocityPx = minV))
    }
}
