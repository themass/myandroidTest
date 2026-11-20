package com.openapi.ks.myapp.agent

/**
 * 界面状态与纯函数 [reduce]（客户端设计 2.3）。
 *
 * 所有事件组合（合并、插话、排队、排空、取消、失败、重连）都在这里计算，
 * 不依赖界面或网络，因此可以逐条单测。
 */
/** 服务端主动回合的内部指令，界面不显示。 */
const val HIDDEN_KIND = "proactive"

enum class BubbleState { SYNCED, SENDING, FAILED, QUEUED, MERGED, STEER_PENDING, STEERED, ANSWERED }

data class AgentBubble(
    /** 本地键：发送中用 client_message_id，之后换成服务端 message_id。 */
    val key: String,
    val messageId: String?,
    val role: String,
    val content: String,
    val state: BubbleState,
    val turnId: String? = null,
    /** 同一次提问的全部候选回复 id（升序）；只有一条回复时为空。左右滑动在这些 id 之间切换。 */
    val altIds: List<String> = emptyList(),
    /** 当前显示的是第几个候选（从 0 起）。 */
    val altIndex: Int = 0,
)

data class QueuedInput(val messageId: String, val text: String, val position: Int?)

data class ActiveTurn(
    val turnId: String,
    val text: String,
    val steps: List<AgentStep>,
    val userMessageIds: List<String>,
)

data class PendingConfirmation(val confirmationId: String, val title: String, val summary: String)

data class AgentChatState(
    val bubbles: List<AgentBubble> = emptyList(),
    val active: ActiveTurn? = null,
    val queued: List<QueuedInput> = emptyList(),
    val pendingConfirmations: List<PendingConfirmation> = emptyList(),
    /** 连续使用提醒（服务端下发的文案），用户关掉后清空。 */
    val usageReminder: String? = null,
    val connection: ConnectionState = ConnectionState.RECONNECTING,
    /** 收到 gone：服务端历史应整体覆盖本地列表（服务端是权威）。 */
    val needsHistoryReload: Boolean = false,
    val lastError: String? = null,
    /** 已应用的最后一个事件 id。流 id 单调递增，小于等于它的事件视为重复（客户端设计 10）。 */
    val lastAppliedEventId: String? = null,
)

/** 本地发送：先出现在列表里（sending），拿到回执后再更新。 */
fun AgentChatState.withLocalSending(clientMessageId: String, text: String): AgentChatState =
    copy(bubbles = bubbles + AgentBubble(clientMessageId, null, "user", text, BubbleState.SENDING))

/** 回执到达：用服务端 id 替换本地键，并按处置设置状态。 */
fun AgentChatState.withReceipt(clientMessageId: String, receipt: SubmitReceipt): AgentChatState {
    val next = when (receipt.disposition) {
        Disposition.QUEUED -> BubbleState.QUEUED
        Disposition.MERGED -> BubbleState.MERGED
        Disposition.STEERED -> BubbleState.STEER_PENDING
        Disposition.STARTED, null -> BubbleState.SYNCED
    }
    val queuedAdd = if (receipt.disposition == Disposition.QUEUED) {
        listOf(QueuedInput(receipt.messageId, textOf(clientMessageId), receipt.queuePosition))
    } else {
        emptyList()
    }
    return copy(
        bubbles = bubbles.map {
            if (it.key == clientMessageId) it.copy(key = receipt.messageId, messageId = receipt.messageId, state = next, turnId = receipt.turnId) else it
        },
        queued = (queued.filterNot { it.messageId == receipt.messageId } + queuedAdd),
    )
}

/** 发送失败：气泡变红，等用户点重试（复用同一个 client_message_id，服务端去重）。 */
fun AgentChatState.withSendFailed(clientMessageId: String): AgentChatState =
    copy(bubbles = bubbles.map { if (it.key == clientMessageId) it.copy(state = BubbleState.FAILED) else it })

private fun AgentChatState.textOf(key: String): String = bubbles.firstOrNull { it.key == key }?.content ?: ""

/** 把一次传输层更新应用到状态。重复事件（id 不大于已应用的）直接忽略。 */
fun reduce(state: AgentChatState, update: StreamUpdate): AgentChatState = when (update) {
    is StreamUpdate.Connection -> state.copy(connection = update.state)
    is StreamUpdate.Event -> {
        val event = update.event
        if (event is SessionEvent.Reconnect) state
        else if (isDuplicate(state.lastAppliedEventId, event.eventId)) state
        else applyEvent(state, event).copy(lastAppliedEventId = event.eventId.ifEmpty { state.lastAppliedEventId })
    }
}

private fun isDuplicate(last: String?, current: String): Boolean {
    if (last == null || current.isEmpty()) return false
    return compareStreamIds(current, last) <= 0
}

/** Redis Stream id 形如 "<毫秒>-<序号>"，按数值比较。 */
internal fun compareStreamIds(a: String, b: String): Int {
    val (am, aseq) = splitStreamId(a)
    val (bm, bseq) = splitStreamId(b)
    return if (am != bm) am.compareTo(bm) else aseq.compareTo(bseq)
}

private fun splitStreamId(id: String): Pair<Long, Long> {
    val ms = id.substringBefore('-').toLongOrNull() ?: 0L
    val seq = id.substringAfter('-', "0").toLongOrNull() ?: 0L
    return ms to seq
}

private fun applyEvent(state: AgentChatState, event: SessionEvent): AgentChatState = when (event) {
    // 主动回合的内部指令（kind=proactive）不进列表；随后到来的角色回复照常显示。
    is SessionEvent.MessageCreated -> if (event.kind == HIDDEN_KIND) state else applyMessageCreated(state, event)

    is SessionEvent.TurnSnapshot -> state.copy(
        active = ActiveTurn(event.turnId, event.text, state.active?.steps.orEmpty(), event.userMessageIds),
    )

    is SessionEvent.TurnStarted -> state.copy(
        active = ActiveTurn(event.turnId, state.active?.takeIf { it.turnId == event.turnId }?.text.orEmpty(),
            emptyList(), event.userMessageIds),
        bubbles = markBubbles(state.bubbles, event.userMessageIds, BubbleState.ANSWERED),
        queued = state.queued.filterNot { it.messageId in event.userMessageIds },
    )

    is SessionEvent.TurnRestarted -> state.copy(
        active = ActiveTurn(event.turnId, "", emptyList(), event.userMessageIds),
    )

    is SessionEvent.Delta -> {
        val current = state.active?.takeIf { it.turnId == event.turnId }
            ?: ActiveTurn(event.turnId, "", emptyList(), emptyList())
        state.copy(active = current.copy(text = current.text + event.text))
    }

    is SessionEvent.Step -> {
        val current = state.active?.takeIf { it.turnId == event.turnId }
            ?: ActiveTurn(event.turnId, "", emptyList(), emptyList())
        state.copy(active = current.copy(steps = current.steps + event.step))
    }

    is SessionEvent.InputMerged -> state.copy(bubbles = setState(state.bubbles, event.messageId, BubbleState.MERGED))

    is SessionEvent.InputSteered -> state.copy(
        bubbles = setState(state.bubbles, event.messageId, BubbleState.STEERED),
        queued = state.queued.filterNot { it.messageId == event.messageId },
    )

    is SessionEvent.InputQueued -> {
        val text = state.bubbles.firstOrNull { it.messageId == event.messageId }?.content.orEmpty()
        state.copy(
            bubbles = setState(state.bubbles, event.messageId, BubbleState.QUEUED),
            queued = state.queued.filterNot { it.messageId == event.messageId } +
                QueuedInput(event.messageId, text, event.position),
        )
    }

    is SessionEvent.InputWithdrawn -> state.copy(
        bubbles = state.bubbles.filterNot { it.messageId == event.messageId },
        queued = state.queued.filterNot { it.messageId == event.messageId },
    )

    // 排空：这些输入已交给模型，不能再撤回；队列里移除（客户端设计 3.2）。
    is SessionEvent.QueueDrained -> state.copy(
        bubbles = markBubbles(state.bubbles, event.messageIds, BubbleState.ANSWERED),
        queued = state.queued.filterNot { it.messageId in event.messageIds },
    )

    is SessionEvent.Done -> finishTurn(state, event.turnId, event.assistantMessageId, event.steeredMessageIds)
        // 重新生成的新回复和旧回复同属一组候选：服务端才知道组里有几条，重新拉历史（其余走原来的流程）。
        .let { if (event.altGroup != null) it.copy(needsHistoryReload = true) else it }

    // 取消只结束当前回合，不清空排队（D §6.6）。
    is SessionEvent.TurnCancelled -> finishTurn(state, event.turnId, event.assistantMessageId, emptyList())

    is SessionEvent.Failed -> state.copy(
        active = state.active?.takeIf { event.turnId == null || it.turnId != event.turnId },
        lastError = event.code,
    )

    is SessionEvent.ConfirmRequired -> state.copy(
        pendingConfirmations = state.pendingConfirmations.filterNot { it.confirmationId == event.confirmationId } +
            PendingConfirmation(event.confirmationId, event.title, event.summary),
    )

    is SessionEvent.ActionDone -> state.copy(
        pendingConfirmations = state.pendingConfirmations.filterNot { it.confirmationId == event.confirmationId },
    )

    is SessionEvent.UsageReminder -> state.copy(usageReminder = event.text)

    is SessionEvent.Gone -> state.copy(needsHistoryReload = true)

    is SessionEvent.Reconnect -> state
}

private fun applyMessageCreated(state: AgentChatState, event: SessionEvent.MessageCreated): AgentChatState {
    // 本机发出的消息已经有本地气泡：只补上服务端 id，不重复添加（多端同步时也是同一条）。
    val existing = state.bubbles.indexOfFirst { it.messageId == event.messageId }
    if (existing >= 0) return state
    val local = event.clientMessageId?.let { key -> state.bubbles.indexOfFirst { it.key == key } } ?: -1
    if (local >= 0) {
        val bubble = state.bubbles[local]
        return state.copy(bubbles = state.bubbles.toMutableList().also {
            it[local] = bubble.copy(key = event.messageId, messageId = event.messageId, state = BubbleState.SYNCED)
        })
    }
    return state.copy(bubbles = state.bubbles + AgentBubble(
        key = event.messageId, messageId = event.messageId, role = event.role, content = event.content,
        state = BubbleState.SYNCED))
}

private fun finishTurn(state: AgentChatState, turnId: String, assistantMessageId: String?, steeredIds: List<String>): AgentChatState {
    val active = state.active?.takeIf { it.turnId == turnId }
    val text = active?.text.orEmpty()
    val withAnswer = if (assistantMessageId != null && text.isNotEmpty()) {
        state.bubbles + AgentBubble(assistantMessageId, assistantMessageId, "assistant", text, BubbleState.SYNCED, turnId)
    } else {
        state.bubbles
    }
    return state.copy(
        bubbles = markBubbles(withAnswer, steeredIds, BubbleState.ANSWERED),
        active = if (state.active?.turnId == turnId) null else state.active,
        queued = state.queued.filterNot { it.messageId in steeredIds },
    )
}

private fun setState(bubbles: List<AgentBubble>, messageId: String, state: BubbleState): List<AgentBubble> =
    bubbles.map { if (it.messageId == messageId) it.copy(state = state) else it }

private fun markBubbles(bubbles: List<AgentBubble>, ids: List<String>, state: BubbleState): List<AgentBubble> =
    if (ids.isEmpty()) bubbles else bubbles.map { if (it.messageId in ids) it.copy(state = state) else it }

/** 服务端历史整体覆盖本地列表（收到 gone、或首次进入会话）。服务端是权威（客户端设计 4.1）。 */
fun AgentChatState.withHistory(history: List<AgentBubble>): AgentChatState =
    copy(bubbles = history, needsHistoryReload = false)

/** 撤回成功：本地立即移除（服务端随后也会删除对应消息）。 */
fun AgentChatState.withoutQueued(messageId: String): AgentChatState =
    copy(
        bubbles = bubbles.filterNot { it.messageId == messageId },
        queued = queued.filterNot { it.messageId == messageId },
    )

/** 重试失败的发送：回到发送中，复用同一个 client_message_id（服务端去重）。 */
fun AgentChatState.withRetrying(clientMessageId: String): AgentChatState =
    copy(bubbles = bubbles.map { if (it.key == clientMessageId) it.copy(state = BubbleState.SENDING) else it })

/** 删除一条消息（服务端已确认删除）。服务端同时会把它从模型的对话线程里移除。 */
fun AgentChatState.withoutMessage(messageId: String): AgentChatState =
    copy(bubbles = bubbles.filterNot { it.messageId == messageId })

/** 编辑后的消息替换原来那条（保持位置）。 */
fun AgentChatState.withEditedMessage(updated: AgentBubble): AgentChatState =
    copy(bubbles = bubbles.map { if (it.messageId == updated.messageId) updated.copy(key = it.key) else it })

/** 关掉连续使用提醒。 */
fun AgentChatState.withoutUsageReminder(): AgentChatState = copy(usageReminder = null)

/** 切换候选回复：用新选中的那条替换同组里原来显示的那条（保持位置）。 */
fun AgentChatState.withSelectedAlternative(selected: AgentBubble): AgentChatState =
    copy(bubbles = bubbles.map {
        if (it.messageId != null && it.messageId in selected.altIds) selected.copy(key = it.key) else it
    })

/** 操作失败的提示（例如回合进行中服务端返回 409）。 */
fun AgentChatState.withError(message: String?): AgentChatState = copy(lastError = message)
