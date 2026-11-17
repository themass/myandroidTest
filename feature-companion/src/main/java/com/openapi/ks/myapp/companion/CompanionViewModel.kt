package com.openapi.ks.myapp.companion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openapi.ks.myapp.avatar.ProactiveKind
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/**
 * 角色扮演界面的状态持有者。
 *
 * 房间连接藏在 [CompanionTransport] 后面，默认是离线替身，所以这个界面在没接
 * LiveKit、没后端的情况下也能完整走一遍：发消息、看流式回复、看形象切状态。
 *
 * 有 [CompanionApi] 时：建会话、恢复待确认动作、轮询主动消息收件箱、确认动作都走服务端。
 */
class CompanionViewModel(
    private val api: CompanionApi? = null,
    private val transport: CompanionTransport = FakeCompanionTransport(),
    /** 收件箱轮询间隔。主动消息不需要秒级到达，30 秒一次够用且省电。 */
    private val inboxPollMillis: Long = DEFAULT_INBOX_POLL_MILLIS,
) : ViewModel() {

    private val _state = MutableStateFlow(CompanionUiState())
    val state: StateFlow<CompanionUiState> = _state.asStateFlow()

    private var characterId: String? = null
    private var collectJob: Job? = null
    private var startupJob: Job? = null
    private var inboxJob: Job? = null
    private var connectionJob: Job? = null

    /**
     * 消息 id 的自增源。界面上「删除某条消息」「某条待确认」都按 id 定位，
     * 不能用列表下标——删掉一条之后下标全变，会操作到另一条上。
     */
    private var nextMessageId = 1L

    private fun newMessage(
        role: MessageRole,
        text: String,
        streaming: Boolean = false,
        proactive: ProactiveKind = ProactiveKind.REPLY,
        confirmationId: String? = null,
    ) = CompanionMessage(
        role = role,
        text = text,
        streaming = streaming,
        proactive = proactive,
        confirmationId = confirmationId,
        id = nextMessageId++,
    )

    /** 没有 [CompanionApi] 时直接连替身，用于预览与离线调试。 */
    fun start(characterId: String) {
        // 同一个角色：已经连上/正在重连/启动流程还在跑，都不能再建一个房间（会泄漏上一个 Room）。
        // 只有出错或空闲（启动流程已结束）才允许重试。
        if (this.characterId == characterId) {
            val connection = _state.value.connection
            val inFlight = startupJob?.isActive == true
            if (inFlight || connection == CompanionConnection.CONNECTED || connection == CompanionConnection.RECONNECTING) return
        }
        this.characterId = characterId
        _state.update { it.copy(connection = CompanionConnection.CONNECTING, error = null) }
        startupJob?.cancel()
        startupJob = viewModelScope.launch {
            try {
                val session = api?.createSession(characterId) ?: offlineSession(characterId)
                if (transport is LiveKitCompanionTransport) observeConnection()
                restoreHistory(session)
                if (transport !is LiveKitCompanionTransport) listen()
                transport.connect(session)
                if (transport is LiveKitCompanionTransport) listen() else observeConnection()
                startInboxPolling()
                restorePendingActions()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        connection = CompanionConnection.ERROR,
                        error = e.message ?: "连接失败",
                    )
                }
            }
        }
    }

    fun send() {
        val current = _state.value
        if (!current.canSend) return
        val text = current.input.trim()
        transport.sendText(text)
        _state.update {
            it.copy(
                input = "",
                messages = it.messages +
                    newMessage(MessageRole.USER, text) +
                    // 先占位，收到 TextDelta 往里追加；TurnDone 时关掉 streaming。
                    newMessage(MessageRole.ASSISTANT, "", streaming = true),
            )
        }
    }

    /**
     * 取角色的展示信息（名字、一句话人设），顶栏用。
     *
     * 从角色列表里找当前这个，而不是 `character(id)` 单条接口——顶栏只需要展示字段，
     * 列表接口已经在别处用过、字段齐全，少一次只为一个名字的请求形状差异。
     * 没有 api（预览/测试）或找不到时返回 null，界面退回「陪伴」标题。
     */
    suspend fun characterSummary(roleId: String): CharacterSummary? {
        val client = api ?: return null
        return runCatching { client.characters(limit = 50, offset = 0) }.getOrNull()
            ?.items?.firstOrNull { it.id == roleId }
    }

    /** 删除一条消息（只影响本地会话；服务端没有「删单条消息」的接口）。 */
    fun deleteMessage(messageId: Long) {
        _state.update { st -> st.copy(messages = st.messages.filterNot { it.id == messageId }) }
    }

    /**
     * 编辑已发出去的一条用户消息：原文放回输入框，并从列表移除。
     *
     * 与「对话」页的编辑语义一致（发送改好的版本 = 一次新的发送）。陪伴页没有服务端单条
     * 删除接口，所以这里只动本地列表；否则长按菜单里的「编辑」会是一个点了没反应的按钮。
     */
    fun beginEdit(messageId: Long) {
        val current = _state.value
        val message = current.messages.firstOrNull { it.id == messageId } ?: return
        if (message.role != MessageRole.USER) return
        _state.update {
            it.copy(
                input = message.text,
                messages = it.messages.filterNot { m -> m.id == messageId },
            )
        }
    }

    /**
     * 重新回答最后一条用户消息。
     *
     * chatkit 的「重新回答」在「最后一条是用户消息」时出现。删掉她的回复后就会露出这个入口，
     * 所以它必须真的能重新问一遍——否则又是一个点了没反应的按钮。
     * 不重复添加用户气泡（那条已经在了），只补一个流式占位。
     */
    fun regenerateLastReply() {
        val current = _state.value
        if (current.connection != CompanionConnection.CONNECTED) return
        val lastUser = current.messages.lastOrNull { it.role == MessageRole.USER } ?: return
        if (current.messages.lastOrNull()?.streaming == true) return
        transport.sendText(lastUser.text)
        _state.update {
            it.copy(messages = it.messages + newMessage(MessageRole.ASSISTANT, "", streaming = true))
        }
    }

    fun onInputChange(value: String) {
        _state.update { it.copy(input = value) }
    }

    /**
     * 切换她**怎么回**：文字 or 语音。
     *
     * 这个开关以前只改本地状态、从没到达服务端——服务端永远推 TTS，客户端永远播。
     * 现在它直接控制播放：VOICE = 放她的声音，TEXT = 静音播放。
     *
     * 说清楚一件事：服务端仍然会合成音频（没有对应的接口字段可关），我们只是不播。
     * 真正的「别合成」要等服务端加开关；在那之前，至少用户按下它时耳朵里的声音会停。
     */
    fun setOutput(output: CompanionOutput) {
        _state.update { it.copy(output = output) }
        // 通话期间不受它影响：通话页里音频必须开着，否则等于打了一通没声音的电话。
        if (!_state.value.callActive) {
            transport.setPlaybackAudible(output == CompanionOutput.VOICE)
        }
        // 不断开房间：文字与语音共用同一个 agent。
    }

    fun toggleOutput() {
        setOutput(if (_state.value.output == CompanionOutput.TEXT) CompanionOutput.VOICE else CompanionOutput.TEXT)
    }

    /**
     * 退出通话页时静音她的播放。
     *
     * 房间不断开（通话只是形态，不是另一条连接），但「挂断」在用户心里就是
     * 「不该再响了」。以前退出后 TTS 还在放，只能在文字页里找「语音回答」开关关掉。
     */
    fun setPlaybackAudible(audible: Boolean) {
        transport.setPlaybackAudible(audible)
    }

    /** 进入/退出全屏通话形态。 */
    fun setCallActive(active: Boolean) {
        _state.update { it.copy(callActive = active) }
    }

    /**
     * 进入通话，并把麦克风真正发布到房间。
     *
     * [micAllowed] 是「用户是否给了录音权限」，不是「用户想不想说话」：
     * 没给权限时仍然进通话页（她那边还在说，字幕还能看），只是以静音状态进去，
     * 而不是把人挡在门外。静音可以在页内再打开。
     */
    fun startCall(micAllowed: Boolean) {
        _state.update { it.copy(callActive = true, isMuted = !micAllowed) }
        transport.setMicEnabled(micAllowed)
        // 通话里音频一定要开：这是通话的定义，与文字模式那个开关无关。
        transport.setPlaybackAudible(true)
    }

    /** 挂断回到聊天现场。房间不断：通话只是形态，不是另一条连接。 */
    fun endCall() {
        transport.setMicEnabled(false)
        _state.update { it.copy(callActive = false, isMuted = false) }
        // 回到聊天页后，是否继续出声由「语音回答」开关决定（默认文字 = 静音）。
        transport.setPlaybackAudible(_state.value.output == CompanionOutput.VOICE)
    }

    /**
     * 静音开关。状态先改、再通知传输层：麦克风发布是异步的，如果等它回来再更新按钮，
     * 用户点了会有近一秒「没反应」，会以为按钮坏了。
     */
    private var audioPreviewActive = false

    /** 本地试听不发到 RTC；结束后按当前通话/静音意图恢复，退出通话不误开麦。 */
    fun setAudioPreviewActive(active: Boolean) {
        audioPreviewActive = active
        val current = _state.value
        transport.setMicEnabled(!active && current.callActive && !current.isMuted)
        transport.setPlaybackAudible(!active && (current.callActive || current.output == CompanionOutput.VOICE))
    }

    fun toggleMute() {
        val muted = !_state.value.isMuted
        _state.update { it.copy(isMuted = muted) }
        transport.setMicEnabled(!muted && !audioPreviewActive)
    }

    /**
     * 把传输层的连接状态映射进界面。
     *
     * 以前界面只看「有没有建过连」：重连中、已断开全都显示成「已连接」（或者永远
     * 「正在接通」），掉线时用户看着字幕停住却找不到原因。这里改成唯一来源。
     * DISCONNECTED 归到 ERROR 而不是 IDLE：它需要一个可读的错误提示，IDLE 是
     * 「还没开始连」，两者在界面上的表现必须不同。
     */
    private fun observeConnection() {
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            transport.connection.collect { transportConnection ->
                val mapped = when (transportConnection) {
                    TransportConnection.CONNECTING -> CompanionConnection.CONNECTING
                    TransportConnection.CONNECTED -> CompanionConnection.CONNECTED
                    TransportConnection.RECONNECTING -> CompanionConnection.RECONNECTING
                    TransportConnection.DISCONNECTED -> CompanionConnection.ERROR
                }
                _state.update { current ->
                    current.copy(
                        connection = mapped,
                        error = when (mapped) {
                            CompanionConnection.CONNECTED, CompanionConnection.RECONNECTING -> null
                            CompanionConnection.ERROR -> current.error
                            CompanionConnection.CONNECTING -> null
                            CompanionConnection.IDLE -> current.error
                        },
                    )
                }
            }
        }
    }

    /** 展开/收起形象区。默认收起。 */
    fun toggleAvatarExpanded() {
        _state.update { it.copy(avatarExpanded = !it.avatarExpanded) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun onAvatarEvent(raw: String) {
        val state = Regex("\"type\"\\s*:\\s*\"state\".*?\"state\"\\s*:\\s*\"([^\"]+)\"")
            .find(raw)?.groupValues?.get(1)
        val emotion = Regex("\"type\"\\s*:\\s*\"emotion\".*?\"emotion\"\\s*:\\s*\"([^\"]+)\"")
            .find(raw)?.groupValues?.get(1)
        _state.update {
            it.copy(
                avatarState = state ?: it.avatarState,
                emotion = emotion ?: it.emotion,
            )
        }
    }

    /**
     * 她说话的瞬时振幅 0..1（来自远端音频轨的 PCM）。
     *
     * 直接从 transport 转发，不进 [CompanionUiState]：这是每秒几十次的高频采样，
     * 塞进 UI state 会让整棵界面树按音频帧重组。形象层单独 collect 它。
     */
    val mouthLevel: StateFlow<Float> get() = transport.mouthLevel

    /** 用户自己的音量 0..1（本地麦克风采集轨）。驱动通话页底部波形。 */
    val selfLevel: StateFlow<Float> get() = transport.selfLevel

    /**
     * 用户点了她一下。
     *
     * 只做两件事：把触摸上报给传输层（由 agent 决定回不回），以及重置她的「正在说话」
     * 之外的状态——不在这里拼一句假的回复。她自己该不该回应、回应什么，是 agent 的判断；
     * 客户端替她说话会让「互动」变成一个骗人的动画。
     */
    fun onAvatarTapped(region: String) {
        transport.sendTouch(region)
    }

    /** 用户确认一个待执行动作。服务端执行器是权威，确认与否都以它的结果为准。 */
    fun confirmAction(confirmationId: String) = resolveAction(confirmationId, accepted = true)

    fun cancelAction(confirmationId: String) = resolveAction(confirmationId, accepted = false)

    /**
     * 停止收听：取消事件收集与收件箱轮询，不断开房间。界面不在前台时调用，
     * 省掉无人看的轮询；再次 [start] 时会重新拉起。
     */
    fun stopListening() {
        collectJob?.cancel()
        collectJob = null
        inboxJob?.cancel()
        inboxJob = null
        connectionJob?.cancel()
        connectionJob = null
    }

    fun dismissIncomingCall() {
        _state.update { it.copy(incomingCall = null) }
    }

    private fun resolveAction(confirmationId: String, accepted: Boolean) {
        removeConfirmation(confirmationId)
        val remote = api ?: return
        viewModelScope.launch {
            try {
                remote.confirmAction(confirmationId, accepted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 动作没执行成功要让用户知道，而不是静默当作做完了。
                _state.update { it.copy(error = e.message ?: "动作处理失败") }
            }
        }
    }

    /**
     * 把这段对话之前说过的话读回来。
     *
     * 为什么必须先做这一步再谈其他：服务端 `resolve_chat_session()` 保证「一个用户 + 一个角色
     * 只有一段历史，语音和文字都写进它」，所以历史**一直在写**，但界面从来不读——
     * 用户关掉重开看到的是空页，会以为记录丢了。这不是新功能，是补上一直缺的那一半。
     *
     * 失败不提示：读历史失败不该挡住用户开始说话，静默降级成「从空开始」即可。
     */
    private suspend fun restoreHistory(session: SessionResponse) {
        val remote = api ?: return
        val chatId = session.chatSessionId ?: return
        val history = runCatching { remote.history(chatId) }.getOrNull() ?: return
        if (history.isEmpty()) return
        _state.update { st ->
            // 已经有消息就不覆盖：连接期间可能已经收到几条新的。
            if (st.messages.isNotEmpty()) return@update st
            st.copy(
                messages = history.map { item ->
                    newMessage(
                        role = if (item.isUser) MessageRole.USER else MessageRole.ASSISTANT,
                        text = item.text,
                    )
                },
            )
        }
    }

    private suspend fun restorePendingActions() {
        val remote = api ?: return
        val pending = runCatching { remote.pendingActions() }.getOrElse { return }
        if (pending.isEmpty()) return
        _state.update { st ->
            val existing = st.messages.mapNotNull { it.confirmationId }.toSet()
            st.copy(
                messages = st.messages + pending
                    .filterNot { it.confirmationId in existing }
                    .map { action ->
                        newMessage(
                            role = MessageRole.ASSISTANT,
                            text = action.description.ifBlank { action.action },
                            confirmationId = action.confirmationId,
                        )
                    },
            )
        }
    }

    private fun startInboxPolling() {
        val remote = api ?: return
        inboxJob?.cancel()
        inboxJob = viewModelScope.launch {
            while (true) {
                runCatching { remote.inbox() }.getOrNull()?.forEach { deliverProactive(it) }
                delay(inboxPollMillis)
            }
        }
    }

    private fun deliverProactive(message: InboxMessage) {
        val kind = when {
            message.channel == "call" -> ProactiveKind.CALL
            message.trigger == "event_due" -> ProactiveKind.EVENT
            message.trigger.startsWith("schedule") -> ProactiveKind.GREETING
            else -> ProactiveKind.REPLY
        }
        _state.update { st ->
            if (st.messages.any { it.text == message.text && it.proactive == kind }) return@update st
            st.copy(
                messages = st.messages + newMessage(
                    role = MessageRole.ASSISTANT,
                    text = message.text,
                    proactive = kind,
                ),
                incomingCall = if (kind == ProactiveKind.CALL) kind else st.incomingCall,
            )
        }
    }

    private fun listen() {
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            transport.events.collect { event ->
                when (event) {
                    is CompanionEvent.TextDelta -> appendToStreaming(event.text)
                    is CompanionEvent.AvatarJson -> onAvatarEvent(event.raw)
                    is CompanionEvent.Proactive -> _state.update { st ->
                        // 主动消息单独成条并打标：用户需要一眼看出是她自己开口的。
                        st.copy(
                            messages = st.messages + newMessage(
                                role = MessageRole.ASSISTANT,
                                text = event.text,
                                proactive = event.kind,
                            ),
                        )
                    }

                    is CompanionEvent.NeedsConfirmation -> _state.update { st ->
                        // 待确认动作不当作普通消息：它在界面上要变成一对按钮。
                        st.copy(
                            messages = st.messages + newMessage(
                                role = MessageRole.ASSISTANT,
                                text = event.prompt,
                                confirmationId = event.confirmationId,
                            ),
                        )
                    }

                    is CompanionEvent.IncomingCall -> _state.update {
                        it.copy(incomingCall = ProactiveKind.CALL)
                    }

                    CompanionEvent.TurnDone -> _state.update { st ->
                        st.copy(messages = st.messages.map {
                            if (it.streaming) it.copy(streaming = false) else it
                        })
                    }

                    is CompanionEvent.Error -> _state.update {
                        it.copy(
                            connection = CompanionConnection.ERROR,
                            error = event.message,
                        )
                    }
                }
            }
        }
    }

    private fun removeConfirmation(confirmationId: String) {
        _state.update { st ->
            st.copy(messages = st.messages.filterNot { it.confirmationId == confirmationId })
        }
    }

    private fun appendToStreaming(delta: String) {
        _state.update { st ->
            val idx = st.messages.indexOfLast { it.streaming }
            if (idx < 0) return@update st
            st.copy(messages = st.messages.toMutableList().also {
                it[idx] = it[idx].copy(text = it[idx].text + delta)
            })
        }
    }

    /** 没有后端时给一个本地会话，让界面照常可用。 */
    private fun offlineSession(characterId: String) = SessionResponse(
        sessionId = "local",
        roomName = "local-$characterId",
        token = "",
        livekitUrl = "",
        expiresAt = "",
    )

    override fun onCleared() {
        collectJob?.cancel()
        startupJob?.cancel()
        inboxJob?.cancel()
        connectionJob?.cancel()
        viewModelScope.launch { transport.disconnect() }
    }

    companion object {
        const val DEFAULT_INBOX_POLL_MILLIS = 30_000L
    }
}
