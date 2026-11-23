package com.openapi.app.home.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 抽屉滑动开关的纯函数钉桩。真机踩坑：VRM WebView 的单指拖动在「陪伴」标签上
 * 被父级 ModalNavigationDrawer 在 touch slop 处抢走（页面只收到 down + 1 个 move
 * 就被 ACTION_CANCEL）。对话标签早已为此关掉滑动（按住录音），通话同理：
 * 通话界面里任何横向手势都不该去开抽屉。
 */
class HomeDrawerGestureTest {

    @Test
    fun swipe_allowed_on_non_chat_tab_while_idle() {
        assertTrue(drawerSwipeEnabled(currentIsChat = false, drawerOpen = false, callVisible = false))
    }

    @Test
    fun swipe_blocked_on_chat_tab() {
        assertFalse(drawerSwipeEnabled(currentIsChat = true, drawerOpen = false, callVisible = false))
    }

    @Test
    fun swipe_blocked_while_call_visible() {
        // 通话覆盖层挂起时，陪伴标签上也不能抢走 WebView/舞台的横向手势。
        assertFalse(drawerSwipeEnabled(currentIsChat = false, drawerOpen = false, callVisible = true))
    }

    @Test
    fun swipe_to_close_always_works_when_open() {
        assertTrue(drawerSwipeEnabled(currentIsChat = true, drawerOpen = true, callVisible = true))
        assertTrue(drawerSwipeEnabled(currentIsChat = false, drawerOpen = true, callVisible = false))
    }
}
