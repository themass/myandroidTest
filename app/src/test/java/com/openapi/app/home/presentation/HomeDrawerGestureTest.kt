package com.openapi.app.home.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 抽屉滑动开关的纯函数钉桩。真机踩坑：VRM WebView 的单指拖动在「陪伴」标签上
 * 被父级 ModalNavigationDrawer 在 touch slop 处抢走（页面只收到 down + 1 个 move
 * 就被 ACTION_CANCEL）。对话标签早已为此关掉滑动（按住录音），通话同理：
 * 通话界面里任何横向手势都不该去开抽屉。
 *
 * 频道标签（放映场）整模块禁滑：用户 2026-11-25 明确「频道页所有的页面不应该支持
 * 右滑，没有抽屉，抽屉不是这个模块的功能」——放映场内部所有子页（频道/声音馆/
 * 文字馆/图集/文章）都在这个标签里，一律不开抽屉。
 */
class HomeDrawerGestureTest {

    @Test
    fun swipe_allowed_on_non_chat_non_channels_tab_while_idle() {
        assertTrue(
            drawerSwipeEnabled(
                currentIsChat = false, currentIsChannels = false, drawerOpen = false, callVisible = false,
            )
        )
    }

    @Test
    fun swipe_blocked_on_chat_tab() {
        assertFalse(
            drawerSwipeEnabled(
                currentIsChat = true, currentIsChannels = false, drawerOpen = false, callVisible = false,
            )
        )
    }

    @Test
    fun swipe_blocked_on_channels_tab() {
        // 放映场没有抽屉：任何状态下都不许从左边缘滑出（列表横滚、文章右滑都不该抢）。
        assertFalse(
            drawerSwipeEnabled(
                currentIsChat = false, currentIsChannels = true, drawerOpen = false, callVisible = false,
            )
        )
    }

    @Test
    fun swipe_blocked_while_call_visible() {
        // 通话覆盖层挂起时，陪伴标签上也不能抢走 WebView/舞台的横向手势。
        assertFalse(
            drawerSwipeEnabled(
                currentIsChat = false, currentIsChannels = false, drawerOpen = false, callVisible = true,
            )
        )
    }

    @Test
    fun swipe_to_close_always_works_when_open() {
        assertTrue(
            drawerSwipeEnabled(
                currentIsChat = true, currentIsChannels = false, drawerOpen = true, callVisible = true,
            )
        )
        assertTrue(
            drawerSwipeEnabled(
                currentIsChat = false, currentIsChannels = true, drawerOpen = true, callVisible = false,
            )
        )
    }
}
