package com.openapi.ks.myapp.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 主角模式顶栏：名字 + 一行副信息（状态点 · 时长 · 声音 · 静音）+ 右侧菜单，
 * 压成一块左对齐的紧凑区域，把头部上方的空间还给形象。
 * 这里只测副信息行的拼接（纯函数）；原来的形象 / 圆形头像仍走旧顶栏。
 */
class CompanionHeroHeaderTest {

    @Test
    fun nothing_extra_means_no_meta_line() {
        assertNull(heroHeaderMeta(voiceLabel = null, isMuted = false))
    }

    @Test
    fun voice_label_is_prefixed() {
        assertEquals("声音 · 贴心女友", heroHeaderMeta(voiceLabel = "贴心女友", isMuted = false))
    }

    @Test
    fun muted_is_appended_after_voice() {
        assertEquals("声音 · 贴心女友 · 已静音", heroHeaderMeta(voiceLabel = "贴心女友", isMuted = true))
    }

    @Test
    fun muted_without_voice_shows_only_muted() {
        assertEquals("已静音", heroHeaderMeta(voiceLabel = null, isMuted = true))
    }
}
