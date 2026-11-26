package com.openapi.feature.video.prototype

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 弹幕只对在线源开放：本地文件（file:// / content:// / 裸路径）一律不显示弹幕 chip，
 * handler 对本地路径也要 no-op 兜底（防止旧 state 或键盘路径把它打开）。
 */
class LocalDanmakuPolicyTest {

    @Test
    fun `local sources never offer danmaku`() {
        for (state in listOf(
            danmakuState(local = true, on = false),
            danmakuState(local = true, on = true),
        )) {
            assertFalse("local must hide danmaku", ControlsVisibility.danmakuChip(state))
        }
    }

    @Test
    fun `online sources offer danmaku`() {
        assertTrue(ControlsVisibility.danmakuChip(danmakuState(local = false, on = false)))
        assertTrue(ControlsVisibility.danmakuChip(danmakuState(local = false, on = true)))
    }

    @Test
    fun `danmaku toggle is a no-op for local regardless of current flag`() {
        assertFalse(ControlsVisibility.danmakuToggleApplies(local = true, currentlyOn = false))
        assertFalse(ControlsVisibility.danmakuToggleApplies(local = true, currentlyOn = true))
        assertTrue(ControlsVisibility.danmakuToggleApplies(local = false, currentlyOn = false))
        assertTrue(ControlsVisibility.danmakuToggleApplies(local = false, currentlyOn = true))
    }

    private fun danmakuState(local: Boolean, on: Boolean) =
        PlayerControlsState(
            title = "", positionMs = 0, durationMs = 0, playing = false, buffering = false,
            controlsVisible = true, landscape = false, locked = false, fullscreen = false,
            subtitleText = "", subtitleEnabled = false, subtitleDelayMs = 0,
            speed = 1f, danmakuOn = on, speedLabel = "1×", local = local,
        )
}
