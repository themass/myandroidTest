package com.openapi.feature.chat.presentation.ui

import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.speech.audio.AudioPlayback
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A43-#10（QUEUE 模式）：排空续发建出来的用户气泡带「排队发送」标记。
 *
 * 投诉的根因是排队模式下回合结束后气泡**突然冒出来**，用户以为消息丢了
 * （或自己没按过发送）。标记把「这条是之前排的队」显式说出来。
 * 映射层只做搬运（fromQueue → ChatItem.queuedSend），判定在 ReplyStreamer 的排空路径。
 */
class ChatItemMapperQueuedSendTest {

    private fun map(vararg messages: ChatMessage) = ChatItemMapper.map(
        messages.toList(), AudioPlayback(), null, emptySet(), emptySet(),
    )

    @Test
    fun drainedBubbleCarriesQueuedSendFlag() {
        val item = map(
            ChatMessage(
                id = "q-0", role = ChatRole.USER, text = "排空发的",
                createdAtMillis = 1L, fromQueue = true,
            ),
        ).single()
        assertEquals(true, item.queuedSend)
    }

    @Test
    fun normalSendHasNoQueuedSendFlag() {
        val item = map(
            ChatMessage(id = "u1", role = ChatRole.USER, text = "直接发的", createdAtMillis = 1L),
        ).single()
        assertEquals(false, item.queuedSend)
    }
}
