package com.openapi.ks.myapp.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class HeroSideDrawerTest {

    @Test
    fun `release build only has the caption toggle`() {
        val rows = heroDrawerRows(
            previewAvailable = false, previewSpeaker = null, captionsVisible = true,
            theme = CallTheme.Default,
        )
        assertEquals(listOf(DrawerAction.TOGGLE_CAPTIONS, DrawerAction.THEME), rows.map { it.action })
        assertEquals("字幕 · 开", rows[0].label)
    }

    @Test
    fun `debug build lists both listen clips vertically then the caption toggle`() {
        val rows = heroDrawerRows(
            previewAvailable = true, previewSpeaker = null, captionsVisible = false,
            theme = CallTheme.Default,
        )
        assertEquals(
            listOf(
                DrawerAction.LISTEN_USER, DrawerAction.LISTEN_AI,
                DrawerAction.TOGGLE_CAPTIONS, DrawerAction.THEME,
            ),
            rows.map { it.action },
        )
        assertEquals(listOf("▶ 用户试听", "▶ AI 试听", "字幕 · 关", "背景 · 深海"), rows.map { it.label })
    }

    @Test
    fun `playing clip row turns into a stop button`() {
        val rows = heroDrawerRows(
            previewAvailable = true, previewSpeaker = "ai", captionsVisible = true,
            theme = CallTheme.Default,
        )
        assertEquals("▶ 用户试听", rows[0].label)
        assertEquals("■ 停止 AI 试听", rows[1].label)
        assertEquals(true, rows[1].active)
    }

    @Test
    fun `背景行排在字幕下面，名字跟着当前主题走`() {
        val preset = heroDrawerRows(
            previewAvailable = false, previewSpeaker = null, captionsVisible = true,
            theme = CallTheme.Presets.first { it.id == "warm_night" },
        )
        assertEquals("背景 · 暖夜", preset.last().label)
        assertEquals(false, preset.last().active)

        val custom = heroDrawerRows(
            previewAvailable = false, previewSpeaker = null, captionsVisible = true,
            theme = customCallTheme(120f),
        )
        assertEquals("背景 · 自定义 120°", custom.last().label)
        assertEquals(true, custom.last().active)
    }

    @Test
    fun `声音行在背景下面，只在服务端下发了音色时出现`() {
        val noVoice = heroDrawerRows(
            previewAvailable = false, previewSpeaker = null, captionsVisible = true,
            theme = CallTheme.Default, voiceLabel = null,
        )
        assertEquals(
            listOf(DrawerAction.TOGGLE_CAPTIONS, DrawerAction.THEME),
            noVoice.map { it.action },
        )

        val withVoice = heroDrawerRows(
            previewAvailable = false, previewSpeaker = null, captionsVisible = true,
            theme = CallTheme.Default, voiceLabel = "痣妖姐姐",
        )
        assertEquals(
            listOf(DrawerAction.TOGGLE_CAPTIONS, DrawerAction.THEME, DrawerAction.VOICE),
            withVoice.map { it.action },
        )
        assertEquals("声音 · 痣妖姐姐", withVoice.last().label)
        // 服务端还没开换音色接口：声音行只读，点了不响应（active=false 兼作「不可点」标记）。
        assertEquals(false, withVoice.last().active)
    }
}
