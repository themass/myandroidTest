package com.openapi.chat.domain.usecase

import com.openapi.chat.domain.model.ChatMessage
import com.openapi.chat.domain.model.ChatStreamEvent
import com.openapi.chat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow

/** 读取当前会话历史。 */
class LoadHistoryUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(): List<ChatMessage> =
        repository.loadHistory(repository.currentSessionId())
}

/** 从本地删除一条消息（只影响本机记录）。 */
class DeleteMessageUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(messageId: String) = repository.deleteMessage(messageId)
}

/**
 * 发送一条用户消息，流式接收回复。
 *
 * 这里只做「会话 id」的补齐，协议细节（SSE 帧解析、历史组装）留在 repository 实现里。
 */
class SendMessageUseCase(private val repository: ChatRepository) {
    operator fun invoke(
        text: String,
        characterId: Int?,
        roleId: String? = null,
        persistUserMessage: Boolean = true,
    ): Flow<ChatStreamEvent> =
        repository.sendMessageStream(
            sessionId = repository.currentSessionId(),
            userText = text,
            characterId = characterId,
            roleId = roleId,
            persistUserMessage = persistUserMessage,
        )
}
