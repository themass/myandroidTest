package com.openapi.ks.myapp.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class HeroSideDrawerTest {

    @Test
    fun `release build only has the caption toggle`() {
        val rows = heroDrawerRows(previewAvailable = false, previewSpeaker = null, captionsVisible = true)
        assertEquals(listOf(DrawerAction.TOGGLE_CAPTIONS), rows.map { it.action })
        assertEquals("字幕 · 开", rows.single().label)
    }

    @Test
    fun `debug build lists both listen clips vertically then the caption toggle`() {
        val rows = heroDrawerRows(previewAvailable = true, previewSpeaker = null, captionsVisible = false)
        assertEquals(
            listOf(DrawerAction.LISTEN_USER, DrawerAction.LISTEN_AI, DrawerAction.TOGGLE_CAPTIONS),
            rows.map { it.action },
        )
        assertEquals(listOf("▶ 用户试听", "▶ AI 试听", "字幕 · 关"), rows.map { it.label })
    }

    @Test
    fun `playing clip row turns into a stop button`() {
        val rows = heroDrawerRows(previewAvailable = true, previewSpeaker = "ai", captionsVisible = true)
        assertEquals("▶ 用户试听", rows[0].label)
        assertEquals("■ 停止 AI 试听", rows[1].label)
        assertEquals(true, rows[1].active)
    }
}
