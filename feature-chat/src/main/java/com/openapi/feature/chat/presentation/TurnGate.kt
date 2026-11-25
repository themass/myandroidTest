package com.openapi.feature.chat.presentation

/**
 * 「这一轮占不占着会话」的**读侧**唯一入口（最小收敛，不改写侧）。
 *
 * 为什么要有这一个类型：曾经同一个判断散在七处读 `state.sending`
 * （`SendActions` 四处、`HistoryActions.loadOlder`、`ResendActions` 两处），每处自己决定
 * 「挡住、排队还是放行」——于是同一个「正在生成」在不同入口行为不一致：
 * 纯文字排队、历史加载被**静默吞掉**（任务 4 里用户翻到顶没反应的那条）、重试直接 return。
 * 集中到一处之后，「生成中到底什么能做什么不能做」是一个能在纯 JVM 测试里钉住的表。
 *
 * **边界**（刻意不再扩）：
 * - 只收敛**读侧**。写侧仍然只有一处真相：[ChatUiState.sending] 由
 *   [ReplyStreamer.stream] 置真、由它的两个收尾分支（自然结束 / 停止）置假；
 *   `streaming` 由附件侧（AttachmentSender）另外维护。把 `ReplyStreamer.replyJob`、
 *   `ChatViewModel.attachmentJob`、data 层 `MigrationGuard.busy` 这三个「忙」标志合并成
 *   一个状态机是另一件事：三者生命周期不同（回复任务、附件任务、服务端回合闸门），
 *   强行统一要么把附件的「可停」语义拖进文字流，要么在 ViewModel 里再造一层同步代码。
 *   `attachmentJob` 的**句柄覆盖 bug** 就地修在写侧（ChatViewModel 两处赋值前先 cancel 旧句柄）。
 * - 不接管 UI 的禁用样式（`ChatInputBar` 读 `sending`/`generating` 画按钮态）：那是显示，
 *   不是决策；决策变了显示会跟着状态走。
 * - 不新增状态：全部从既有 [ChatUiState] 快照推导，无构造时序问题。
 */
internal data class ChatTurnGate(
    val sending: Boolean,
    val loadingOlder: Boolean,
    val hasMoreHistory: Boolean,
    val editingMessageId: String?,
    val hasAttachmentDrafts: Boolean,
    val hasLocationDraft: Boolean,
) {

    companion object {
        fun of(state: ChatUiState): ChatTurnGate = ChatTurnGate(
            sending = state.sending,
            loadingOlder = state.loadingOlder,
            hasMoreHistory = state.hasMoreHistory,
            editingMessageId = state.editingMessageId,
            hasAttachmentDrafts = state.drafts.isNotEmpty(),
            hasLocationDraft = state.locationDraft != null,
        )
    }

    /**
     * 发送这一击的去向。[BlockedTurnRunning] 就是「被这一轮挡住」这个原因本身——
     * 编辑的目标消息可能已被这一轮改变、附件要等上传窗口、位置同理，都不适合塞进队列。
     */
    enum class SendPath {
        /** 没有回合在进行：直接发。 */
        Direct,

        /** 生成中的纯文字：按 Enter 顺序入队（FIFO），回合自然结束后由 ReplyStreamer 续发。 */
        Queue,

        /** 生成中的编辑/附件/位置：挡住（现状保持静默，不排队也不直发）。 */
        BlockedTurnRunning,
    }

    /** 发送点击的完整判定（含编辑态与草稿——输入框文字看调用方的 trim 结果，不在这里判）。 */
    fun sendPath(): SendPath = when {
        !sending -> SendPath.Direct
        editingMessageId == null && !hasAttachmentDrafts && !hasLocationDraft -> SendPath.Queue
        else -> SendPath.BlockedTurnRunning
    }

    /**
     * 已经拿到手的纯文字（语音转写、队列续发）：生成中入队，否则直发。
     * 与 [sendPath] 分开是因为这些路径没有草稿/编辑上下文，判据只有「回合占没占着」。
     */
    fun textSendPath(): SendPath = if (sending) SendPath.Queue else SendPath.Direct

    /** 选附件进托盘：生成中不接（上传窗口属于下一个回合）。 */
    val allowAttachmentPick: Boolean get() = !sending

    /** 重试失败消息 / 重新生成：都要起新回合，生成中挡住（与旧的 `sending` 闸一致）。 */
    val allowResend: Boolean get() = !sending

    /**
     * 加载更早的一页：**不看 sending**。读历史是只读操作，与当前回合互不相干；
     * 旧代码在生成中静默吞掉这次请求（任务 4 删掉手动按钮后尤其不能留——滚到顶没反应）。
     */
    val allowLoadOlder: Boolean get() = hasMoreHistory && !loadingOlder
}
