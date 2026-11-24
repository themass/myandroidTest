package com.openapi.feature.video.screening

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A24（后退直接退出 app）状态机返回判定。
 *
 * 根因：ScreeningHome 原来只在「频道内页」一层挂了 BackHandler，
 * 图集封面列表级 / 文字·声音馆选择器级自己深层的 BackHandler 在这些级是 disabled 的，
 * 返回键没人接 → 穿透到系统默认 → 结束任务回桌面。
 * 修复后的出口必须覆盖全部非根态、一次只清一层，且优先级与 `screen` 的 when 分支一致
 * （播放 > 频道内页 > 图集 > 文字馆 > 声音馆）。
 */
class ScreeningBackNavigationTest {

    @Test
    fun `根层（全部 flag 平）返回交回系统默认`() {
        assertEquals(ScreeningBackStep.NONE, step())
    }

    // —— 回归主体：开任务里判掉的那三种级，现在都必须有一层可退 ——

    @Test
    fun `图集封面列表级返回退回顾层 不再穿透退出`() {
        assertEquals(ScreeningBackStep.GALLERY, step(galleryOpen = true))
    }

    @Test
    fun `文字馆选择器级返回退回顾层`() {
        assertEquals(ScreeningBackStep.TEXT_HUB, step(textHubOpen = true))
    }

    @Test
    fun `声音馆选择器级返回退回顾层`() {
        assertEquals(ScreeningBackStep.SOUND_HUB, step(soundHubOpen = true))
    }

    @Test
    fun `频道内页返回退回顾层（原出口语义保留）`() {
        assertEquals(ScreeningBackStep.CHANNEL_PAGE, step(channelPageOpen = true))
    }

    // —— 播放器态：由内层无条件 BackHandler 消费，外层判成 PLAYER 供 enabled 排除 ——

    @Test
    fun `播放器态交还给播放器自己的出口 外层不抢`() {
        assertEquals(ScreeningBackStep.PLAYER, step(playing = true))
    }

    @Test
    fun `播放器在栈上时优先于其它任何层`() {
        assertEquals(ScreeningBackStep.PLAYER,
            step(playing = true, channelPageOpen = true, galleryOpen = true, textHubOpen = true, soundHubOpen = true))
    }

    // —— 优先级与 `screen` when 分支一致：一次只退最上面一层 ——

    @Test
    fun `频道内页盖在图集之上时先退频道内页`() {
        assertEquals(ScreeningBackStep.CHANNEL_PAGE, step(channelPageOpen = true, galleryOpen = true))
    }

    @Test
    fun `频道内页盖在文字馆之上时先退频道内页`() {
        assertEquals(ScreeningBackStep.CHANNEL_PAGE, step(channelPageOpen = true, textHubOpen = true))
    }

    @Test
    fun `图集级优先于文字·声音馆级`() {
        assertEquals(ScreeningBackStep.GALLERY, step(galleryOpen = true, textHubOpen = true, soundHubOpen = true))
    }

    @Test
    fun `文字馆级优先于声音馆级`() {
        assertEquals(ScreeningBackStep.TEXT_HUB, step(textHubOpen = true, soundHubOpen = true))
    }

    private fun step(
        playing: Boolean = false,
        channelPageOpen: Boolean = false,
        galleryOpen: Boolean = false,
        textHubOpen: Boolean = false,
        soundHubOpen: Boolean = false,
    ) = screeningBackStep(playing, channelPageOpen, galleryOpen, textHubOpen, soundHubOpen)
}
