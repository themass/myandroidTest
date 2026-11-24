package com.openapi.feature.video.screening

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A22（字幕开关幂等无反馈）+ A23（键盘弹出时不许自动收走带）抽出来的纯判定的 JVM 单测。
 * UI insets 本身没法在 JVM 测，这里钉的是判定口径：什么算「有可开来源」、什么时候打勾、
 * 反馈和 hint 怎么说、自动收起要不要过键盘这道闸。
 */
class ScreeningCaptionA22A23Test {

    private val item = SubtitleItem(1L, "中文", "中文", "SRT", "12 KB")

    // ---- hasCaptionSourceChoice ----

    @Test
    fun `三条来源任一存在就算有可开字幕`() {
        assertTrue(hasCaptionSourceChoice(listOf(item), false, false))
        assertTrue(hasCaptionSourceChoice(null, true, false))
        assertTrue(hasCaptionSourceChoice(null, false, true))
    }

    @Test
    fun `服务端列表取到过且为空才算确认没有轨道`() {
        assertFalse(hasCaptionSourceChoice(emptyList(), false, false))
    }

    @Test
    fun `列表还没取到时不断言服务端没有，只按导入和实时判`() {
        assertFalse(hasCaptionSourceChoice(null, false, false))
        assertTrue(hasCaptionSourceChoice(null, true, false))
    }

    // ---- captionOffSettingSelected ----

    @Test
    fun `关闭态且有可开来源才显示选中勾`() {
        assertTrue(captionOffSettingSelected(0, true))
    }

    @Test
    fun `空态下关闭字幕不再打勾`() {
        assertFalse(captionOffSettingSelected(0, false))
    }

    @Test
    fun `开着别的字幕源时关闭项不勾`() {
        assertFalse(captionOffSettingSelected(2, true))
        assertFalse(captionOffSettingSelected(3, true))
        assertFalse(captionOffSettingSelected(4, true))
    }

    // ---- captionNoSourceHint / captionAlreadyOffNotice ----

    @Test
    fun `空态 hint 把没有可开字幕的原因并列说清`() {
        val hint = captionNoSourceHint(emptyList(), false)
        assertTrue(hint.startsWith("现在没有可开的字幕："))
        assertTrue(hint.contains("服务端没有字幕轨"))
        assertTrue(hint.contains("没有导入过字幕文件"))
        assertTrue(hint.contains("这个片源不能现场识别"))
    }

    @Test
    fun `列表还没取到时 hint 不写死服务端没有轨道`() {
        val hint = captionNoSourceHint(null, true)
        assertFalse(hint.contains("服务端没有字幕轨"))
        assertFalse(hint.contains("不能现场识别"))
        assertTrue(hint.contains("没有导入过字幕文件"))
    }

    @Test
    fun `已有可开来源时反馈引导去选来源，不说没字幕`() {
        val notice = captionAlreadyOffNotice(listOf(item), false, false)
        assertTrue(notice.contains("本来就是关的"))
        assertTrue(notice.contains("选下面列出的字幕来源"))
        assertFalse(notice.contains("没有可开的字幕轨"))
    }

    @Test
    fun `空态反馈说这个视频没有可开的字幕轨`() {
        val notice = captionAlreadyOffNotice(emptyList(), false, false)
        assertTrue(notice.contains("本来就是关的"))
        assertTrue(notice.contains("这个视频没有可开的字幕轨"))
    }

    // ---- shouldAutoHideChrome ----

    private fun chrome(
        full: Boolean = true,
        playing: Boolean = true,
        locked: Boolean = false,
        panelOpen: Boolean = false,
        chromeVisible: Boolean = true,
        draft: String = "",
        imeVisible: Boolean = false,
    ) = shouldAutoHideChrome(full, playing, locked, panelOpen, chromeVisible, draft, imeVisible)

    @Test
    fun `影院态播放中无输入无键盘才自动收走带`() {
        assertTrue(chrome())
    }

    @Test
    fun `键盘弹出时不自动收走带（A23 闸门）`() {
        assertFalse(chrome(imeVisible = true))
    }

    @Test
    fun `原有闸门原样保留`() {
        assertFalse(chrome(full = false))
        assertFalse(chrome(playing = false))
        assertFalse(chrome(locked = true))
        assertFalse(chrome(panelOpen = true))
        assertFalse(chrome(chromeVisible = false))
        assertFalse(chrome(draft = "有字"))
    }
}
