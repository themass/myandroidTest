package com.openapi.feature.video.screening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 服务端字幕映射规则的 JVM 单测。
 * 网络层本身不在这里测（那要起真请求），测的是纯函数：文案映射、格式推断、内容粗校验、id 提取。
 */
class ScreeningSubtitleTest {

    @Test
    fun `语言代码映射到中文界面文案`() {
        assertEquals("中文", subtitleLangLabel("zh"))
        assertEquals("中文", subtitleLangLabel(" ZH-CN "))
        assertEquals("繁体中文", subtitleLangLabel("zh-hant"))
        assertEquals("英文", subtitleLangLabel("eng"))
        assertEquals("日文", subtitleLangLabel("ja"))
        assertEquals("韩文", subtitleLangLabel("ko"))
    }

    @Test
    fun `未知语言原样显示，缺失语言给兜底`() {
        assertEquals("th", subtitleLangLabel("th"))
        assertEquals("其他语言", subtitleLangLabel(null))
        assertEquals("其他语言", subtitleLangLabel("  "))
    }

    @Test
    fun `格式优先看 format 字段，其次文件后缀，最后默认 SRT`() {
        assertEquals("VTT", subtitleFormat("vtt", null))
        assertEquals("VTT", subtitleFormat("WebVTT", null))
        assertEquals("VTT", subtitleFormat(null, "字幕.vtt"))
        assertEquals("SRT", subtitleFormat(null, "zh.srt"))
        assertEquals("SRT", subtitleFormat(null, null))
        assertEquals("SRT", subtitleFormat("srt", "名字.vtt")) // format 字段说了算
    }

    @Test
    fun `字节数换算成可读大小`() {
        assertEquals("", subtitleSizeLabel(0))
        assertEquals("", subtitleSizeLabel(-5))
        assertEquals("512 B", subtitleSizeLabel(512))
        assertEquals("20 KB", subtitleSizeLabel(20 * 1024))
        assertEquals("1.5 MB", subtitleSizeLabel(1536 * 1024))
    }

    @Test
    fun `条目副标题只拼非空部分`() {
        val full = SubtitleItem(1, "中文字幕", "中文", "SRT", "20 KB")
        assertEquals("中文 · SRT · 20 KB", full.label)
        val bare = SubtitleItem(2, "没头没尾", "", "SRT", "")
        assertEquals("SRT", bare.label)
    }

    @Test
    fun `能认出 SRT 与 VTT，挡掉 HTML 报错页和空内容`() {
        assertTrue(looksLikeSubtitle("WEBVTT\n\n00:00:01.000 --> 00:00:04.000\n你好"))
        assertTrue(looksLikeSubtitle("1\n00:00:01,000 --> 00:00:04,000\n你好\n"))
        assertFalse(looksLikeSubtitle(null))
        assertFalse(looksLikeSubtitle("   "))
        assertFalse(looksLikeSubtitle("<html><body>404 Not Found</body></html>"))
        assertFalse(looksLikeSubtitle("抱歉，参数错误"))
    }

    @Test
    fun `服务端 id 只从 srv 前缀的 key 里取`() {
        assertEquals("12345", serverIdOf("srv:12345"))
        assertNull(serverIdOf("idx:3"))
        assertNull(serverIdOf("url:https://example.com/a.mp4"))
        assertNull(serverIdOf("sample:reel:5"))
        assertNull(serverIdOf("srv:"))
    }
}
