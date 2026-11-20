package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatUiStateTest {

    /**
     * 断网冷启动的历史加载失败不能伪装成「空会话」：
     * 已显示的消息保留、标记失败（界面据此报错并抑制空状态）、loading 结束。
     */
    @Test
    fun historyFailureKeepsShownMessagesAndMarksFailed() {
        val shown = listOf(
            ChatMessage(id = "1", role = ChatRole.ASSISTANT, text = "之前聊过的话", createdAtMillis = 1L),
        )
        val after = ChatUiState(messages = shown, loading = true).historyLoadFailed()
        assertEquals(shown, after.messages)
        assertTrue(after.historyFailed)
        assertFalse(after.loading)
        assertNotNull(after.error)
    }

    /** 成功重载会把失败标记清掉——重试成功后空状态要能回来。 */
    @Test
    fun defaultStateIsNotFailed() {
        assertFalse(ChatUiState().historyFailed)
    }
}
