package com.openapi.feature.video.screening

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A40「封面经常出不来」的端上侧：加载中没有等待信号（只有一块 img_bg 静底，
 * 用户读成「服务端就是没图」），失败也不区分。修法是扫光盖在 Glide 上、
 * 由 RequestListener 驱动收放，失败落静态 img_bg。
 *
 * 状态机收成纯类 [CoverLoadState]（Glide 回调用 onReady/onFailed 注入），
 * 真机不用装就能看到收放对不对；接线（listener、Box 叠放、onRelease clear）
 * 用结构钉子钉住——漏接任何一条，扫光就会永远转下去或根本不出现。
 */
class ScreeningCoverShimmerTest {

    @Test
    fun `请求进行中显示扫光，图落地即收`() {
        val s = CoverLoadState()
        assertTrue("新请求默认在加载中", s.showShimmer)
        s.onReady()
        assertFalse("图已落地，扫光必须收掉", s.showShimmer)
    }

    @Test
    fun `失败不再扫光，落静态底`() {
        val s = CoverLoadState()
        s.onFailed()
        assertFalse("失败后不能再扫光（那是骗用户还在加载）", s.showShimmer)
        assertTrue("要能区分出这是失败态", s.failed)
    }

    @Test
    fun `同一个 view 复用重新发请求时扫光重新起`() {
        val s = CoverLoadState()
        s.onFailed()
        s.onStart()
        assertTrue("复用/重试后再进加载态", s.showShimmer)
        assertFalse(s.failed)
    }

    @Test
    fun `RemoteCover 结构钉子：扫光叠在 Glide 上、listener 驱动、onRelease 对齐 clear`() {
        val src = File("src/main/java/com/openapi/feature/video/screening/ScreeningFeedUi.kt").readText()
        val body = bodyOf(src, "internal fun RemoteCover")
        assertTrue("加载中必须叠 ShimmerPlaceholder", body.contains("ShimmerPlaceholder"))
        assertTrue("Glide 链必须挂 listener 驱动状态", body.contains(".listener("))
        assertTrue("失败态要留 img_bg 静态底", body.contains("ImagePlaceholderRes"))
        assertTrue("onRelease 必须 clear，防止复用后回调打进已废弃的 state",
            body.contains("Glide.with(view).clear(view)"))
    }

    private fun bodyOf(text: String, fn: String): String {
        val start = text.indexOf(fn)
        assertTrue("找不到 $fn", start >= 0)
        // 从签名后第一个「{」开始配对大括号——从「(」配对只会截到签名本身。
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
