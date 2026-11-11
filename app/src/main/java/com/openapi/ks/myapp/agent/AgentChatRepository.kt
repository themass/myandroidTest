package com.openapi.ks.myapp.agent

import kotlinx.coroutines.flow.Flow

/**
 * 新 App 的对话仓库（客户端设计 2.1）：发送走 REST（立即回执），接收走会话事件流。
 * 旧的 `ChatRepository` 保留给旧接口，两者并存，由 ChatDependencies 选择。
 */
interface AgentChatRepository {
    /** 冷流：收集即连接，取消即断开。 */
    fun events(sessionId: String, lastEventId: String?): Flow<StreamUpdate>
    suspend fun createSession(roleId: String, locale: String): String
    suspend fun submit(sessionId: String, text: String, clientMessageId: String): SubmitReceipt
    suspend fun cancel(sessionId: String)
    suspend fun queued(sessionId: String): List<QueuedInput>
    suspend fun withdraw(sessionId: String, messageId: String)
    suspend fun clearQueue(sessionId: String)
    suspend fun retryFailedQueue(sessionId: String)
    suspend fun retryTurn(sessionId: String, turnId: String)
    suspend fun continueReply(sessionId: String, messageId: String)
    suspend fun regenerate(sessionId: String, messageId: String)
    suspend fun history(sessionId: String, before: String?): List<AgentBubble>
}

class AgentChatRepositoryImpl(
    private val api: AgentApi,
    private val reconnector: AgentEventReconnector,
) : AgentChatRepository {

    override fun events(sessionId: String, lastEventId: String?): Flow<StreamUpdate> =
        reconnector.updates(sessionId, lastEventId)

    override suspend fun createSession(roleId: String, locale: String): String = api.createSession(roleId, locale)

    override suspend fun submit(sessionId: String, text: String, clientMessageId: String): SubmitReceipt =
        api.submit(sessionId, text, clientMessageId)

    override suspend fun cancel(sessionId: String) = api.cancel(sessionId)

    override suspend fun queued(sessionId: String): List<QueuedInput> = api.queue(sessionId)

    override suspend fun withdraw(sessionId: String, messageId: String) = api.withdraw(sessionId, messageId)

    override suspend fun clearQueue(sessionId: String) = api.clearQueue(sessionId)

    override suspend fun retryFailedQueue(sessionId: String) = api.retryFailedQueue(sessionId)

    override suspend fun retryTurn(sessionId: String, turnId: String) = api.retryTurn(sessionId, turnId)

    override suspend fun continueReply(sessionId: String, messageId: String) = api.continueReply(sessionId, messageId)

    override suspend fun regenerate(sessionId: String, messageId: String) = api.regenerate(sessionId, messageId)

    override suspend fun history(sessionId: String, before: String?): List<AgentBubble> = api.history(sessionId, before)
}
