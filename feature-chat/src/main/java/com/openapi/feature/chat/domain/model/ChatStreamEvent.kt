package com.openapi.feature.chat.domain.model

/**
 * 一次发送消息过程中产生的流式事件。
 *
 * 对应后端 `/chat/stream.json` 的 SSE 帧（StreamChunk{id, sessionId, delta, done, error}），
 * 但 domain 层不认识 SSE，只认这个密封接口——data 层负责把协议细节翻译成它。
 */
sealed interface ChatStreamEvent {
    /**
     * 收到一段增量文本，UI 应该把它追加到当前占位气泡上（打字机效果）。
     * [messageId] 是本地的助手气泡 id；done 帧带来正式 id 后，ViewModel 直接替换。
     */
    data class Delta(val messageId: String, val text: String) : ChatStreamEvent

    /**
     * 本轮回复结束。[fullText] 是完整文本。
     * [assistantId] / [userId] 是服务端正式消息 id（done 帧里的 assistantMessageId / userMessageId）；
     * 回复为空时 [assistantId] 为 null，重新生成时 [userId] 为 null。两者都是 null 时才需要兜底拉取历史。
     */
    data class Done(val assistantId: String?, val userId: String?, val fullText: String) : ChatStreamEvent

    /**
     * 请求失败或服务端在流中报错。[userMessageId] 是服务端已保存的用户消息 id（错误帧带来时才有），
     * 有它时重试直接用服务端 id，没有则退回到用本地 id 重发文字。
     *
     * [code] 是**机读**的错误类别（见下面两个常量），不是给人看的文字：data 层只能给出协议级的
     * 中文兜底串，用户真正看到的这句话要由 presentation 层按系统语言取（[ChatStrings] 端口），
     * 所以这里必须把类别带上来，而不是让 UI 去猜 message 的字面量。
     */
    data class Error(val message: String, val userMessageId: String? = null, val code: String? = null) : ChatStreamEvent {
        companion object {
            /**
             * 帧间看门狗触发：本轮开始（或上一个内容帧）之后 [watchdogMs] 内没有任何内容帧。
             * 见 [com.openapi.feature.chat.data.remote.TurnEventCollector]。
             */
            const val CODE_TURN_TIMEOUT = "turn_timeout"

            /**
             * 取消上一轮之后服务端仍然回 409：这一轮确实还占着会话。
             * 见 [com.openapi.feature.chat.data.remote.AgentBackedChatRepository]。
             */
            const val CODE_PREVIOUS_TURN_RUNNING = "turn_still_running"
        }

        // 每次出错都记日志（包括服务端返回的错误帧、HTTP 状态、网络异常），方便从 logcat 查真实原因
        init { android.util.Log.w("ChatStream", "stream error: $message") }
    }
}
