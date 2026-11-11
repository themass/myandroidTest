package com.openapi.feature.chat.domain.usecase

import com.openapi.feature.chat.domain.model.HistoryPage
import com.openapi.feature.chat.domain.model.ChatStreamEvent
import com.openapi.feature.chat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow

/** 读取当前会话的一页历史（beforeId 为 null 时取最新一页）。 */
class LoadHistoryUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(beforeId: Long? = null, limit: Int = 30): HistoryPage =
        repository.loadHistoryPage(repository.currentSessionId(), beforeId, limit)
}

/** 删除一条消息（服务端 + 本地缓存）。 */
class DeleteMessageUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(messageId: String): Boolean =
        repository.deleteMessage(repository.currentSessionId(), messageId)
}

/**
 * 发送一条用户消息，流式接收回复。
 *
 * 这里只做「会话 id」的补齐，协议细节（SSE 帧解析、会话 id 回写）留在 repository 实现里。
 */
class SendMessageUseCase(private val repository: ChatRepository) {
    operator fun invoke(
        text: String,
        characterId: Int?,
        roleId: String? = null,
        persistUserMessage: Boolean = true,
        clientMessageId: String? = null,
    ): Flow<ChatStreamEvent> =
        repository.sendMessageStream(
            sessionId = repository.currentSessionId(),
            userText = text,
            characterId = characterId,
            roleId = roleId,
            persistUserMessage = persistUserMessage,
            clientMessageId = clientMessageId,
        )
}
