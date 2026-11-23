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
    suspend fun selectAlternative(messageId: String): AgentBubble
    suspend fun confirmAction(confirmationId: String, accepted: Boolean)
    suspend fun pendingActions(): List<PendingConfirmation>
    suspend fun consent(): AgentConsent
    suspend fun updateConsent(trainingConsent: Boolean? = null, proactiveEnabled: Boolean? = null): AgentConsent
    suspend fun exportData(): String
    suspend fun history(sessionId: String, before: String?): List<AgentBubble>
    suspend fun deleteMessage(messageId: String)
    suspend fun editMessage(messageId: String, content: String): AgentBubble
    suspend fun reminders(includeDone: Boolean = false): List<AgentReminder>
    suspend fun cancelReminder(reminderId: String)
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

    override suspend fun selectAlternative(messageId: String): AgentBubble = api.selectAlternative(messageId)

    override suspend fun confirmAction(confirmationId: String, accepted: Boolean) =
        api.confirmAction(confirmationId, accepted)

    override suspend fun pendingActions(): List<PendingConfirmation> = api.pendingActions()

    override suspend fun consent(): AgentConsent = api.consent()

    override suspend fun updateConsent(trainingConsent: Boolean?, proactiveEnabled: Boolean?) =
        api.updateConsent(trainingConsent, proactiveEnabled)

    override suspend fun exportData(): String = api.exportData()

    override suspend fun history(sessionId: String, before: String?): List<AgentBubble> = api.history(sessionId, before)
    override suspend fun deleteMessage(messageId: String) = api.deleteMessage(messageId)
    override suspend fun editMessage(messageId: String, content: String): AgentBubble = api.editMessage(messageId, content)
    override suspend fun reminders(includeDone: Boolean): List<AgentReminder> = api.reminders(includeDone)
    override suspend fun cancelReminder(reminderId: String) = api.cancelReminder(reminderId)
}
