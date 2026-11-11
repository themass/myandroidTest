package com.openapi.ks.myapp.agent

/**
 * 新 chat-agent 协议（服务端 D §7）的客户端模型。
 *
 * 与服务端事件表一一对应；未知事件在解析层丢弃（D §7.4），不让它打断界面。
 */
enum class Disposition {
    STARTED, MERGED, STEERED, QUEUED;

    companion object {
        fun parse(value: String?): Disposition? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

/** POST /sessions/{id}/messages 的回执（立即返回，不等回答）。 */
data class SubmitReceipt(
    val messageId: String,
    val turnId: String?,
    val disposition: Disposition?,
    val queuePosition: Int?,
)

/** 服务端定时提醒（到点后角色会主动联系用户）。由对话里的工具 + 确认创建，这里只查看与取消。 */
data class AgentReminder(
    val id: String,
    val sessionId: String,
    val content: String,
    val dueLocal: String,
    val timezone: String,
    val status: String,
)

data class AgentStep(val kind: String, val title: String, val detail: String)

enum class ConnectionState { CONNECTED, RECONNECTING, OFFLINE }

/** 传输层产生的更新：事件，或连接状态变化。 */
sealed interface StreamUpdate {
    data class Event(val event: SessionEvent) : StreamUpdate
    data class Connection(val state: ConnectionState) : StreamUpdate
}

sealed interface SessionEvent {
    val eventId: String

    data class MessageCreated(
        override val eventId: String,
        val messageId: String,
        val clientMessageId: String?,
        val role: String,
        val content: String,
        /** chat（普通消息）或 proactive（服务端到点发起主动回合时的内部指令，不显示）。 */
        val kind: String = "chat",
    ) : SessionEvent

    data class TurnSnapshot(
        override val eventId: String,
        val turnId: String,
        val userMessageIds: List<String>,
        val text: String,
    ) : SessionEvent

    data class TurnStarted(
        override val eventId: String,
        val turnId: String,
        val userMessageIds: List<String>,
    ) : SessionEvent

    /** 合并导致重跑：清空占位气泡，保留已覆盖的用户消息（D §6.3）。 */
    data class TurnRestarted(
        override val eventId: String,
        val turnId: String,
        val userMessageIds: List<String>,
    ) : SessionEvent

    data class Delta(override val eventId: String, val turnId: String, val text: String) : SessionEvent

    data class Step(override val eventId: String, val turnId: String, val step: AgentStep) : SessionEvent

    data class InputMerged(override val eventId: String, val messageId: String, val turnId: String) : SessionEvent

    data class InputSteered(override val eventId: String, val messageId: String, val turnId: String) : SessionEvent

    data class InputQueued(override val eventId: String, val messageId: String, val position: Int?) : SessionEvent

    data class InputWithdrawn(override val eventId: String, val messageId: String) : SessionEvent

    data class QueueDrained(override val eventId: String, val turnId: String, val messageIds: List<String>) : SessionEvent

    data class Done(
        override val eventId: String,
        val turnId: String,
        val assistantMessageId: String?,
        val steeredMessageIds: List<String>,
        /** 这条回复属于一组候选（重新生成产生）：界面据此重新拉历史，显示滑动切换。 */
        val altGroup: String? = null,
    ) : SessionEvent

    data class TurnCancelled(
        override val eventId: String,
        val turnId: String,
        val assistantMessageId: String?,
    ) : SessionEvent

    data class Failed(
        override val eventId: String,
        val turnId: String?,
        val code: String,
        val retryable: Boolean,
    ) : SessionEvent

    data class ConfirmRequired(
        override val eventId: String,
        val confirmationId: String,
        val title: String,
        val summary: String,
    ) : SessionEvent

    data class ActionDone(
        override val eventId: String,
        val confirmationId: String,
        val status: String,
    ) : SessionEvent

    /** 服务端进程即将停机，稍后带 Last-Event-ID 重连（P §3.5）。由传输层处理，不进界面状态。 */
    data class Reconnect(override val eventId: String, val afterMs: Long) : SessionEvent

    /** 续传缓冲已过期，需要重新拉历史（D §7.3）。 */
    data class Gone(override val eventId: String) : SessionEvent
}
