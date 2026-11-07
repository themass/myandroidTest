package com.openapi.chat.domain.model

/**
 * 一次发送消息过程中产生的流式事件。
 *
 * 对应后端 `/chat/stream.json` 的 SSE 帧（StreamChunk{id, sessionId, delta, done, error}），
 * 但 domain 层不认识 SSE，只认这个密封接口——data 层负责把协议细节翻译成它。
 */
sealed interface ChatStreamEvent {
    /**
     * 收到一段增量文本，UI 应该把它追加到当前占位气泡上（打字机效果）。
     * [messageId] 是本地的助手气泡 id；流结束后会被服务端消息 id 替换（见 ChatViewModel 的同步）。
     */
    data class Delta(val messageId: String, val text: String) : ChatStreamEvent

    /** 本轮回复结束，[fullText] 是拼接完整的最终文本；[messageId] 同上，是本地临时 id。 */
    data class Done(val messageId: String, val fullText: String) : ChatStreamEvent

    /** 请求失败或服务端在流中报错。 */
    data class Error(val message: String) : ChatStreamEvent
}
