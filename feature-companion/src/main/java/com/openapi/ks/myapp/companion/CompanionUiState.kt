package com.openapi.ks.myapp.companion

import com.openapi.ks.myapp.avatar.ProactiveKind

/**
 * 她**怎么回**。这是"输出形式"，不是"能不能语音"。
 *
 * 以前这里是 `CompanionMode { TEXT, VOICE }`，名字看着像输出形式，实际语义却是
 * "进不进语音房间"——用户界面上那个「文字 / 语音」分段按钮因此毫无意义：
 * 点「语音」并不会让她回语音条，只是换了条传输路径。用户的原话是「一点意义都没有」。
 */
enum class CompanionOutput { TEXT, VOICE }

enum class CompanionConnection { IDLE, CONNECTING, CONNECTED, ERROR }

enum class MessageRole { USER, ASSISTANT }

data class CompanionMessage(
    val role: MessageRole,
    val text: String,
    /** 正在流式接收：界面显示打字光标，不显示操作按钮。 */
    val streaming: Boolean = false,
    /**
     * 这条消息是不是她主动发起的。主动消息的呈现要与回复区分开——用户需要一眼
     * 看出「没人跟她说话，是她自己开口的」，否则会觉得系统在替他说话。
     */
    val proactive: ProactiveKind = ProactiveKind.REPLY,
    /** 待确认的动作 id。非空时界面显示确认/取消，而不是当作普通消息。 */
    val confirmationId: String? = null,
    /**
     * 这条回复的语音时长（秒）。非空表示有语音条可以播放。
     * 现在还没有数据来源——服务端把 TTS 音轨推给房间，客户端拿到的是连续音频流，
     * 没有"这句话多长"的边界；要真正支持语音条，需要服务端在事件里带上分句与时长。
     */
    val voiceSeconds: Int? = null,
    /**
     * 稳定标识，由 [CompanionViewModel] 生成。
     * 列表里的位置会随删除而变，用下标当 id 会让「删除第 3 条」在下一条消息到达后删错人。
     */
    val id: Long = 0L,
)

data class CompanionUiState(
    /** 她怎么回：文字 or 语音（语音时同时出语音条 + 文字）。 */
    val output: CompanionOutput = CompanionOutput.TEXT,
    /**
     * 是否在全屏通话形态。由用户点「通话」进入，不是常驻状态。
     * 它和 [output] 是两个维度：通话页里也可能只是听声音，不开语音输出。
     */
    val callActive: Boolean = false,
    val connection: CompanionConnection = CompanionConnection.IDLE,
    val messages: List<CompanionMessage> = emptyList(),
    val avatarState: String = "idle",
    val emotion: String? = null,
    val input: String = "",
    val error: String? = null,
    /** 有主动来电时非空：界面切到来电页，而不是插一条消息。 */
    val incomingCall: ProactiveKind? = null,
    /** 形象区是否展开。默认收起——那个占屏 42% 的大框用户根本不知道是什么。 */
    val avatarExpanded: Boolean = false,
) {
    val canSend: Boolean get() = input.isNotBlank() && connection == CompanionConnection.CONNECTED

    /** 当前是否有待确认的动作。有的话输入区要让位给确认按钮。 */
    val pendingConfirmation: String?
        get() = messages.lastOrNull { it.confirmationId != null }?.confirmationId

    /**
     * 是否应该显示"对方正在输入"。最后一条是她的空消息且还在流式接收，就是等待首字阶段。
     */
    val awaitingReply: Boolean
        get() = messages.lastOrNull()?.let { it.role == MessageRole.ASSISTANT && it.text.isEmpty() } == true
}
