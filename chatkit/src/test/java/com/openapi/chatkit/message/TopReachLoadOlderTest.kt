package com.openapi.chatkit.message

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「滚到顶 → 自动加载更早的一页」的判定（任务 4：取代常驻按钮）。
 * 纯函数：组合侧的三道闸（userDragged / programmaticScroll / loadingOlder 联动）
 * 在 [ChatMessageList] 的 snapshotFlow 里，这里钉判据本体。
 */
class TopReachLoadOlderTest {

    @Test
    fun atTopWithMoreHistoryAndIdleTriggers() {
        assertTrue(shouldLoadOlderAtTop(firstVisibleItemIndex = 0, canScrollBackward = false, loadingOlder = false, hasMoreHistory = true))
    }

    @Test
    fun doesNotReTriggerWhileALoadIsInFlight() {
        assertFalse(shouldLoadOlderAtTop(0, canScrollBackward = false, loadingOlder = true, hasMoreHistory = true))
    }

    @Test
    fun doesNotTriggerWhenThereIsNoMoreHistory() {
        assertFalse(shouldLoadOlderAtTop(0, canScrollBackward = false, loadingOlder = false, hasMoreHistory = false))
    }

    @Test
    fun notAtTheTopDoesNotTrigger() {
        // 第一条还没进视口顶端：还能往上滚
        assertFalse(shouldLoadOlderAtTop(0, canScrollBackward = true, loadingOlder = false, hasMoreHistory = true))
        // 首条可见但视口不在顶（第一条上方还有内容被顶出去）
        assertFalse(shouldLoadOlderAtTop(3, canScrollBackward = true, loadingOlder = false, hasMoreHistory = true))
    }

    @Test
    fun indexZeroAloneIsNotEnough() {
        // firstVisibleItemIndex==0 只说明第一条在组合范围内；到顶必须同时不能再往上滚。
        // 这条与上一条合起来钉住「index 0 但 canScrollBackward=true」这个 LazyList 陷阱。
        assertTrue(shouldLoadOlderAtTop(0, canScrollBackward = false, loadingOlder = false, hasMoreHistory = true))
    }
}
