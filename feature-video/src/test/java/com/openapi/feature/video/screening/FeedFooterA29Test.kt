package com.openapi.feature.video.screening

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 页脚空数据守卫（台账 A29，抽象点 18）。
 *
 * 用户点名：声音馆空列表时顶上写着「0 个频道」，底下还写着「继续下滑加载更多」——
 * 空数据没有下一页，页脚那句话是骗人的。守卫收在 [feedFooterShown] 一处，
 * 不在八个调用点各写各的（同 A27 列数、A22 字幕守卫的做法）。
 */
class FeedFooterA29Test {

    @Test
    fun `空列表且取数结束 - 页脚整个不画`() {
        assertFalse(feedFooterShown(loading = false, firstPageLoading = false, empty = true))
    }

    @Test
    fun `首屏取数中列表还空着 - 等待交给扫光骨架，页脚不转圈`() {
        assertFalse(feedFooterShown(loading = true, firstPageLoading = true, empty = true))
    }

    @Test
    fun `有数据取完了 - 画「到底了」`() {
        assertTrue(feedFooterShown(loading = false, firstPageLoading = false, empty = false))
    }

    @Test
    fun `有数据还在翻页 - 画「继续下滑加载更多」`() {
        assertTrue(feedFooterShown(loading = true, firstPageLoading = false, empty = false))
    }

    @Test
    fun `取数失败后列表空 - 列表层给空态文案，页脚不补一句到底了`() {
        // 失败时 loading 已落回 false、列表为空：重试按钮在 FeedError 里，
        // 页脚再写「到底了」会把「没取到」演成「就这些」。
        assertFalse(feedFooterShown(loading = false, firstPageLoading = false, empty = true))
    }
}
