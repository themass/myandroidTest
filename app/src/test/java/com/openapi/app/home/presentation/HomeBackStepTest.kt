package com.openapi.app.home.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 首页根层返回键判定钉桩（用户点名 2026-11-25：「频道首页的后退键还不对，
 * 怎么后退就退出 app 了，不是让你改了么」）。
 *
 * A24 修的是放映场内部层；根层（频道标签的放映场首页）没人接返回 → 系统默认 →
 * singleTask 的 HomeActivity 直接退出 app。这里钉死根层口径：先收覆盖层，
 * 再回主标签，停在对话标签才允许系统退出。
 */
class HomeBackStepTest {

    @Test
    fun `抽屉开着先收抽屉`() {
        assertEquals(HomeBackStep.DRAWER,
            homeBackStep(drawerOpen = true, profilePageOpen = true, currentTab = HomeTab.Channels))
    }

    @Test
    fun `「我的」覆盖页开着先关它（不再退 app）`() {
        assertEquals(HomeBackStep.PROFILE_PAGE,
            homeBackStep(drawerOpen = false, profilePageOpen = true, currentTab = HomeTab.Chat))
    }

    @Test
    fun `频道首页按返回回对话标签而不是退出 app`() {
        assertEquals(HomeBackStep.TAB_TO_CHAT,
            homeBackStep(drawerOpen = false, profilePageOpen = false, currentTab = HomeTab.Channels))
        assertEquals(HomeBackStep.TAB_TO_CHAT,
            homeBackStep(drawerOpen = false, profilePageOpen = false, currentTab = HomeTab.Companion))
    }

    @Test
    fun `停在对话标签才交回系统（正常退出）`() {
        assertEquals(HomeBackStep.NONE,
            homeBackStep(drawerOpen = false, profilePageOpen = false, currentTab = HomeTab.Chat))
    }
}
