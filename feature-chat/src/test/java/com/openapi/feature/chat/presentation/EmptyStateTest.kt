package com.openapi.feature.chat.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * 消息区为空时的三种情况必须分得开。
 *
 * 以前只有一个布尔判断：`if (!state.loading) EmptyChat(...)`。于是**加载失败**
 * 和**真的没有消息**渲染成同一个界面——断网冷启动看到一次全新的空白会话，
 * 用户会以为历史被清空了。这组用例守的就是这个区分。
 */
class EmptyStateTest {

    @Test
    fun aFailedLoadIsNotAnEmptyConversation() {
        val plan = emptyStateFor(ChatUiState(loadError = "没能读到历史：offline"))
        assertTrue("加载失败必须单独成为一种状态，不能落到 Blank", plan is EmptyState.Failed)
    }

    @Test
    fun aFailedLoadKeepsItsReason() {
        val plan = emptyStateFor(ChatUiState(loadError = "没能读到历史：HTTP 500"))
        assertEquals("没能读到历史：HTTP 500", (plan as EmptyState.Failed).message)
    }

    @Test
    fun whileLoadingNothingIsShown() {
        // 加载中先别看空状态：否则会先闪一下「还没有消息」再跳成历史。
        assertEquals(EmptyState.Loading, emptyStateFor(ChatUiState(loading = true)))
    }

    @Test
    fun aNewSessionWithNoMessagesIsBlank() {
        assertEquals(EmptyState.Blank, emptyStateFor(ChatUiState()))
    }

    @Test
    fun loadingWinsOverAStaleFailure() {
        // 重试时清掉旧的失败原因，界面应该回到底部等待，而不是继续显示上一次的错误。
        assertEquals(EmptyState.Loading, emptyStateFor(ChatUiState(loading = true, loadError = "上次的错")))
    }

    @Test
    fun aTightBudgetFailureStillCarriesSomethingReadable() {
        // 异常没有 message 时也不能变成空字符串——那样错误条就是一条空白的红条。
        val text = describeLoadFailure(IOException())
        assertTrue(text.isNotBlank())
        assertTrue(text.contains("IOException"))
    }

    @Test
    fun theReasonIsKeptVerbatim() {
        // 和 ChatErrorBar 同一个原则：给原文，不要换成「请重试」。
        assertTrue(describeLoadFailure(RuntimeException("HTTP 503 service unavailable"))
            .contains("HTTP 503 service unavailable"))
    }
}
