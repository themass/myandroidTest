package com.openapi.app.home.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 抽屉滑动开关的纯函数钉桩。真机取证：整个首页（含频道/放映场/图集全部层级）被同一个
 * ModalNavigationDrawer 包住，旧语义下除「对话」外所有页面右滑都会开出抽屉，和用户在各
 * 页面自己的横向手势（WebView 单指拖动、图集/放映场的横滑翻页）抢手势。用户明确：
 * 「整个频道里所有的页面就没有'右滑'出抽屉的那个需要」。
 *
 * 新语义钉死两点：抽屉关闭时任何界面都不给右滑开（打开只用顶栏菜单按钮）；
 * 抽屉已打开时仍允许右滑关闭。
 */
class HomeDrawerGestureTest {

    @Test
    fun swipe_never_opens_the_drawer_while_closed() {
        assertFalse(drawerSwipeEnabled(drawerOpen = false))
    }

    @Test
    fun swipe_to_close_works_when_open() {
        assertTrue(drawerSwipeEnabled(drawerOpen = true))
    }
}
