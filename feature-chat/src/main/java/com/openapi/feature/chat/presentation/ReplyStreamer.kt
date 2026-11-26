package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.ChatStreamEvent
import com.openapi.feature.chat.domain.model.DeliveryStatus
import com.openapi.feature.chat.domain.model.OutgoingAttachment
import com.openapi.feature.chat.domain.repository.ChatRepository
import com.openapi.feature.chat.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 把一条消息发给大模型并流式接收回复，负责助手气泡的生命周期、打字机节奏、停止和失败处理。
 * 从 ChatViewModel 拆出来。依赖通过构造参数传入，ViewModel 只做转发。
 */
internal class ReplyStreamer(
    private val repository: ChatRepository,
    private val state: MutableStateFlow<ChatUiState>,
    private val scope: CoroutineScope,
    private val sendMessage: SendMessageUseCase,
    /** 附件发送是否还在进行中（结束时决定 streaming 标记）。 */
    private val attachmentActive: () -> Boolean,
    /** 兜底：用服务端历史对齐一次。 */
    private val reconcile: suspend () -> Unit,
    /** 新会话出现时同步会话列表。 */
    private val syncSessions: suspend () -> Unit,
    private val strings: ChatStrings,
) {

    private companion object {
        /** 流式文字的释放节拍：约 25 次/秒。 */
        const val REVEAL_TICK_MS = 40L
    }

    /** 文字回复的流式任务。停止按钮通过 [cancel] 取消它。 */
    private var replyJob: Job? = null

    /** 占位气泡的防碰撞序号（见 [newPlaceholder]）。 */
    private var placeholderSeq = 0L

    /**
     * A40「插话」的待接回合：steer 被服务端裁决为 `queued`/`started` 时登记，
     * 本轮事件流自然收尾后取走（见 [runTurn] 末尾），订阅并流式展示服务端排空开的新回合。
     * key = 登记时的会话 id：会话都切走了，登记就没有意义（取出时按当前会话查）。
     */
    private class CaughtTurn(
        val bubbleId: String,
        val skipTurnId: String?,
        val expectedTurnId: String?,
        val userServerId: Long?,
    )
    private val caughtTurns = mutableMapOf<Long, CaughtTurn>()

    /**
     * A40：登记一个「插话」后要接住的回合。两种裁决两种锁扣：
     * - `queued`：回执带的 turn_id 是**当时正在跑**的那条回合（要跳过它，认之后出现的
     *   第一条别的回合）→ 传 [skipTurnId]；
     * - `started`：这条输入自己开了新回合，回执直接带它的 id → 传 [expectedTurnId]。
     */
    fun noteCaughtTurn(
        sessionId: Long,
        skipTurnId: String?,
        bubbleId: String,
        userServerId: Long?,
        expectedTurnId: String? = null,
    ) {
        caughtTurns[sessionId] = CaughtTurn(bubbleId, skipTurnId, expectedTurnId, userServerId)
    }

    fun isReplyActive(): Boolean = replyJob?.isActive == true

    /**
     * 停止当前的文字回复。
     *
     * **两端都要停**：只杀本地 Job 的话，服务端那一轮继续跑，用户接着发下一条就吃
     * 409「上一轮回复还没结束」——「点停止也不好使、怎么发都失败」的成因。
     * 所以这里 fire-and-forget 一条服务端取消：它失败只记日志（仓库实现内部记），
     * 绝不改变本地已经完成的收尾（停止就是停止，取消只是补服务端那一侧）。
     * 用 [scope] 而不是 replyJob：replyJob 马上就要被取消，挂在它下面的清理会被一起取消。
     */
    fun cancel() {
        // 点停止 = 用户的中断意图：排队中的文字一并清空，不能接着发下一条
        // （排空只发生在自然结束的收尾，见 stream 末尾的 drainQueue）。
        // A40：待接的服务端排空回合同样作废——停止就是要停所有后续生成。
        caughtTurns.clear()
        state.update { it.copy(queued = emptyList()) }
        val sessionId = repository.currentSessionId()
        scope.launch { repository.cancelTurn(sessionId) }
        replyJob?.cancel()
    }

    /**
     * 把 [text] 发给大模型并流式接收回复。
     * [userMessageId] 是对应的用户气泡，失败时标成发送失败（可重试）；重新生成时为 null。
     * [persistUser] 为 false 时仓库不再保存用户消息（已经保存过的场景）。
     *
     * 助手气泡的生命周期（只有一条气泡，按 [ChatMessage.key] 定位，id 变化不影响它）：
     * 思考中（pending、无文字）→ 流式（pending、有文字，逐字长出来）→ 完成（pending=false）。
     * 网络收到的文字先放进缓冲区，由一个固定节拍（[REVEAL_TICK_MS]）匀速释放到气泡里，
     * 积压越多释放越快，所以网络一次来一大块也不会整段突然出现。
     */
    fun stream(
        text: String?,
        userMessageId: String?,
        persistUser: Boolean,
        regenerate: Boolean = false,
        syncAfter: Boolean = true,
        /** 重新生成时：复用这条助手消息的气泡（同一个 key、同一个位置），而不是新增一条。 */
        replaceKey: String? = null,
        /** 与 text 同属这一条用户消息的附件（已上传）。空表示纯文字。 */
        attachments: List<OutgoingAttachment> = emptyList(),
        /** 位置消息的坐标（P2-13）；与服务端的顶层 `location` 字段一一对应。 */
        location: com.openapi.feature.chat.domain.model.GeoLocation? = null,
    ) {
        val snapshot = state.value
        // 本轮是否会新建会话（请求时还没有当前会话）：结束后要同步会话列表
        val startsNewSession = repository.currentSessionId() <= 0L
        val existing = replaceKey?.let { key -> snapshot.messages.firstOrNull { it.key == key } }
        val placeholder = existing?.copy(text = "", pending = true) ?: newPlaceholder()
        state.update { current ->
            val messages = if (existing != null) {
                current.messages.map { if (it.key == existing.key) placeholder else it }
            } else {
                current.messages + placeholder
            }
            current.copy(messages = messages, sending = true, streaming = true, error = null)
        }
        userMessageId?.let { setDelivery(it, DeliveryStatus.SENDING) }

        replyJob = scope.launch {
            val events = if (regenerate) {
                repository.regenerateStream(repository.currentSessionId(), snapshot.selectedCharacterId, snapshot.selectedRole?.id)
            } else {
                sendMessage(
                    text.orEmpty(),
                    snapshot.selectedCharacterId,
                    snapshot.selectedRole?.id,
                    persistUser,
                    clientMessageId = userMessageId,
                    attachments = attachments,
                    location = location,
                )
            }
            runTurn(events, placeholder, existing, userMessageId, startsNewSession, syncAfter)
        }
    }

    /**
     * 单回合的收集主体——本机发起（[stream]）与 A40 服务端接住的回合
     * （[noteCaughtTurn] → 接住那条流）共用这一条流水线：占位气泡、打字机节拍、
     * 失败收尾、末尾的「先接服务端排空、再排空本机队列」。
     * 扩展在 [CoroutineScope] 上：打字机的 `reveal` 必须是本次收集的同作用域子任务
     * （停止/取消时一起结束），不能被拆到构造参数 scope 上变成孤儿。
     */
    private suspend fun kotlinx.coroutines.CoroutineScope.runTurn(
        events: kotlinx.coroutines.flow.Flow<ChatStreamEvent>,
        placeholder: ChatMessage,
        existing: ChatMessage?,
        userMessageId: String?,
        startsNewSession: Boolean,
        syncAfter: Boolean,
    ) {
            val buffer = StringBuilder()
            var ended = false
            var failed = false
            var serverId: String? = null
            var serverUserId: String? = null
            var errorUserId: String? = null
            var fullText: String? = null

            val reveal = launch {
                while (true) {
                    kotlinx.coroutines.delay(REVEAL_TICK_MS)
                    if (buffer.isNotEmpty()) {
                        val cut = RevealPacer.safeCut(buffer, RevealPacer.revealCount(buffer.length, ended))
                        val piece = buffer.substring(0, cut)
                        buffer.delete(0, cut)
                        updateByKey(placeholder.key) { it.copy(text = it.text + piece) }
                    } else if (ended) {
                        break
                    }
                }
            }

            try {
                events.collect { event ->
                        when (event) {
                            is ChatStreamEvent.Delta -> buffer.append(event.text)
                            is ChatStreamEvent.Restarted -> {
                                // A40：服务端把这条回合并入新输入后原地重跑（turn.restarted）。
                                // 重跑后服务端只发新一轮的 delta，done 的 fullText 也只含新一轮：
                                // 已显示/已缓冲的旧半截必须作废，不能拼接成「回复重复一遍」。
                                buffer.setLength(0)
                                updateByKey(placeholder.key) { it.copy(text = "") }
                            }
                            is ChatStreamEvent.Done -> {
                                serverId = event.assistantId
                                serverUserId = event.userId
                                fullText = event.fullText
                            }
                            is ChatStreamEvent.Error -> {
                                failed = true
                                errorUserId = event.userMessageId
                                // data 层的 message 是协议级兜底（英文/中文写死在 data 层），
                                // 带 code 的错误按当前语言取 ChatStrings——看门狗和 409
                                // 这两句是用户决定下一步的依据，不能是硬编码中文。
                                state.update { it.copy(error = errorTextFor(event)) }
                            }
                        }
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 用户点了停止。A43-3 / 设计 20261126 §2 D4：**本地收尾先行、同步做完**——
                // 气泡定格（已显示 + 缓冲区里还没释放的半截，pending=false）、发送键复位
                // （sending/streaming=false）、delivery 落定，全都不挂起，点下去就是一帧。
                // 旧实现把 state 更新排在 finishStopped 的 saveMessage（一次网络往返）之后，
                // 慢网络下表现为「按了停止但界面还在生成」。
                reveal.cancel()
                val kept = finishStoppedLocally(placeholder.key, buffer.toString())
                // 停止时一个字都没收到：用户消息标成未发出（可点重试），不当作已发送
                userMessageId?.let { setDelivery(it, if (kept != null) DeliveryStatus.SENT else DeliveryStatus.FAILED) }
                // 服务端会保存已生成的部分，但这里拿不到正式 id：兜底拉一页历史
                if (syncAfter) scope.launch { reconcile() }
                if (startsNewSession) scope.launch { syncSessions() }
                // 保留的内容交给服务端（失败只记日志，见 persistStopped）；NonCancellable 里做：
                // 取消后的第一个挂起点会再次抛出取消，吞掉收尾是对整个协程系统撒谎（整改清单 P3-20）。
                if (kept != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { persistStopped(kept) }
                }
                // A43-#9：停止后**不再弹底部提示**（设计 20261126 §5）。一个字都没收到时
                // 用户看到的可重试入口在气泡自己的 delivery 标（FAILED → 点重试），
                // 底部那条「已停止，消息未发出」是重复提示，删。看门狗/409 那两句
                // （turnTimeout/previousTurnRunning）不在这条路径上，原样保留。
                throw e
            } catch (e: Exception) {
                // 记日志：文字发送偶发失败时，能从 logcat 看到真实原因（之前无法复现原因）
                android.util.Log.w("ChatViewModel", "stream reply failed", e)
                failed = true
                state.update { it.copy(error = e.message) }
            }

            ended = true
            reveal.join()

            // 流正常结束但一个字都没有：撤掉占位气泡，给提示，用户消息标成可重试
            val emptyReply = !failed && fullText.isNullOrEmpty() && buffer.isEmpty()
            state.update { current ->
                current.copy(
                    messages = current.messages.mapNotNull { m ->
                        when {
                            // 用户气泡：拿到正式 id 就直接替换（编辑、删除用它）
                            m.id == userMessageId && (serverUserId ?: errorUserId) != null -> m.copy(id = (serverUserId ?: errorUserId)!!)
                            m.key != placeholder.key -> m
                            // 出错或空回复且一个字都没收到：撤掉占位气泡；已经有内容就保留
                            // 重新生成失败：恢复原来的回复，不能把用户已经看到的内容删掉
                            (failed || emptyReply) && m.text.isEmpty() -> existing?.copy(pending = false)
                            else -> m.copy(id = serverId ?: m.id, text = fullText ?: m.text, pending = false)
                        }
                    },
                    sending = false,
                    streaming = attachmentActive(),
                    error = when {
                        emptyReply -> strings.emptyReply()
                        failed -> current.error ?: strings.replyFailed()
                        else -> current.error
                    },
                )
            }
            // 流结束但没有 done 帧（服务端中途断开、只给了部分文字）：不能当成已完整收到，用户消息标成失败可重试
            val truncated = !failed && !emptyReply && serverId == null && serverUserId == null && userMessageId != null
            if (truncated) {
                state.update { it.copy(error = strings.replyTruncated()) }
            }
            userMessageId?.let { setDelivery(serverUserId ?: errorUserId ?: it, if (failed || emptyReply || truncated) DeliveryStatus.FAILED else DeliveryStatus.SENT) }
            // 正常路径靠 done 帧里的正式 id，不再每轮拉历史；只有缺 id（失败、没有 done 帧、空回复）时兜底拉一页
            if (syncAfter && (failed || serverId == null)) reconcile()
            if (startsNewSession) syncSessions()

            // A40：这一轮结束，先接服务端排空开的新回合（若有待接记录）。
            // 排空回合的文字服务端已经落库、气泡也已由「插话」当场插入，这里只流式回复——
            // userMessageId 传气泡 id 但 persistUser=false（服务端已有这条），
            // Done 带回的正式 user id 会把气泡原位换成服务端 id。
            val sid = repository.currentSessionId()
            val caught = caughtTurns.remove(sid)
            if (caught != null) {
                val user = state.value.messages.firstOrNull { it.id == caught.bubbleId }
                // 占位气泡必须像 stream() 那样先上屏：runTurn 只往 placeholder.key 写字，
                // 不在表里就静默丢掉——排空回合一个字都看不见。
                val placeholder = newPlaceholder()
                state.update { it.copy(messages = it.messages + placeholder, sending = true, streaming = true) }
                val events = repository.takeCaughtTurn(sid, caught.skipTurnId, caught.userServerId, caught.expectedTurnId)
                runTurn(events, placeholder, null, user?.id ?: caught.bubbleId, false, syncAfter = true)
                return
            }

            // 排空 followup 队列（FIFO 取队首续发，保持 Enter 顺序）。只有**自然结束**会走到这里：
            // 用户点停止的那条 catch 分支会 rethrow，永远不会从这里起新回合；
            // cancel() 同时清空了 queued，中断意图明确。空队列零成本。
            val next = state.value.queued.firstOrNull()
            if (next != null) {
                state.update { it.copy(queued = it.queued.drop(1)) }
                val now = System.currentTimeMillis()
                val user = ChatMessage(
                    id = next.id,
                    role = ChatRole.USER,
                    text = next.text,
                    createdAtMillis = now,
                    delivery = DeliveryStatus.SENDING,
                    // A43-#10：排空续发的气泡在回合结束后才出现——带标记让用户认得
                    // 「这是我之前排的队」，而不是以为消息丢了。只有 QUEUE 模式会走到这里
                    // （即时模式 sendText 永不入队），标记随这条消息进渲染。
                    fromQueue = true,
                )
                state.update { it.copy(messages = it.messages + user) }
                stream(next.text, userMessageId = user.id, persistUser = true)
            }
    }

    /**
     * 事件流错误 → 用户看到的这句话。
     *
     * data 层的 [ChatStreamEvent.Error.message] 是协议级兜底（写死在 data 层，不随系统语言变）；
     * 带机读 [ChatStreamEvent.Error.code] 的两类错误（看门狗、取消后仍 409）由这里按
     * [ChatStrings] 端口重新取文案。没有 code 的（服务端 error 帧、重连耗尽）保持原样——
     * 那些话 data 层已经翻译过，且带服务端给的具体原因，不能在这里覆盖掉。
     */
    private fun errorTextFor(event: ChatStreamEvent.Error): String = when (event.code) {
        ChatStreamEvent.Error.CODE_TURN_TIMEOUT -> strings.turnTimeout()
        ChatStreamEvent.Error.CODE_PREVIOUS_TURN_RUNNING -> strings.previousTurnRunning()
        else -> event.message
    }

    /** 返回是否保留了助手内容（false = 一个字都没收到，占位气泡已撤掉）。 */
    /**
     * 停止后的**本地**收尾（A43-3 / D4）：把已显示文字 + 缓冲区里还没释放的半截定格成
     * 正式气泡（pending=false）、发送键复位，全同步、不挂起——点停止就是一帧。
     * 返回定格下来的那条（缓冲区全空时返回 null：撤掉占位气泡，不留下空回复）。
     * 交给服务端是另一件事（[persistStopped]），失败不影响这里。
     */
    private fun finishStoppedLocally(key: String, bufferedText: String): ChatMessage? {
        var kept: ChatMessage? = null
        state.update { current ->
            current.copy(
                messages = current.messages.mapNotNull { m ->
                    if (m.key != key) {
                        m
                    } else {
                        val merged = m.text + bufferedText
                        if (merged.isEmpty()) null else m.copy(text = merged, pending = false).also { kept = it }
                    }
                },
                sending = false,
                streaming = attachmentActive(),
            )
        }
        return kept
    }

    /** 定格的内容交给服务端（服务端会保存已生成的部分；这里拿不到正式 id，靠兜底拉历史对齐）。 */
    private suspend fun persistStopped(kept: ChatMessage) {
        repository.saveMessage(repository.currentSessionId(), kept)
    }

    /**
     * 流式期高频更新：按 key 定位后原位替换，避免每 tick 对整表 map 拷贝（P1-12）。
     */
    fun updateByKey(key: String, transform: (ChatMessage) -> ChatMessage) {
        state.update { current ->
            val index = current.messages.indexOfLast { it.key == key }
            if (index < 0) return@update current
            val previous = current.messages[index]
            val next = transform(previous)
            if (previous === next) return@update current
            val messages = current.messages.toMutableList()
            messages[index] = next
            current.copy(messages = messages)
        }
    }

    fun newPlaceholder(): ChatMessage {
        // 序号必须有：排空续发/A40 接住回合都在上一轮收尾的**同一毫秒**里起新轮，
        // 只用时间戳会让两条占位气泡撞 key——updateByKey(indexOfLast) 会写进
        // 上一条已完成的助手气泡，末尾那张 map 也会把两条一起改掉，上一轮的文字被覆盖。
        val id = "local-pending-" + System.currentTimeMillis() + "-" + placeholderSeq++
        return ChatMessage(
            id = id,
            role = ChatRole.ASSISTANT,
            text = "",
            createdAtMillis = System.currentTimeMillis(),
            pending = true,
        )
    }

    fun setDelivery(messageId: String, status: DeliveryStatus) {
        state.update { current ->
            val i = current.messages.indexOfFirst { it.id == messageId }
            // 没这条消息、或状态本来就是这样：零拷贝（发送状态每秒被检查多次，大多数时候没有变化）
            if (i < 0 || current.messages[i].delivery == status) return@update current
            current.copy(messages = current.messages.updatedAt(i) { it.copy(delivery = status) })
        }
    }

    /** 只复制列表本身、只在 [i] 处换掉一个元素（其余元素保持原实例）。 */
    private inline fun List<ChatMessage>.updatedAt(i: Int, transform: (ChatMessage) -> ChatMessage): List<ChatMessage> {
        val copy = this.toMutableList()
        copy[i] = transform(copy[i])
        return copy
    }
}
