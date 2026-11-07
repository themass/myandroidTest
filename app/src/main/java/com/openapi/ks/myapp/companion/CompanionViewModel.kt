package com.openapi.ks.myapp.companion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openapi.ks.myapp.avatar.ProactiveKind
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
    private var inboxJob: Job? = null

    /** 没有 [CompanionApi] 时直接连替身，用于预览与离线调试。 */
    fun start(characterId: String) {
        if (this.characterId == characterId && collectJob?.isActive == true) return
        this.characterId = characterId
        _state.update { it.copy(connection = CompanionConnection.CONNECTING, error = null) }
        viewModelScope.launch {
            try {
                val session = api?.createSession(characterId) ?: offlineSession(characterId)
                transport.connect(session)
                _state.update { it.copy(connection = CompanionConnection.CONNECTED) }
                listen()
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
                    CompanionMessage(MessageRole.USER, text) +
                    // 先占位，收到 TextDelta 往里追加；TurnDone 时关掉 streaming。
                    CompanionMessage(MessageRole.ASSISTANT, "", streaming = true),
            )
        }
    }

    fun onInputChange(value: String) {
        _state.update { it.copy(input = value) }
    }

    fun toggleMode() {
        _state.update {
            it.copy(
                mode = if (it.mode == CompanionMode.TEXT) CompanionMode.VOICE else CompanionMode.TEXT,
            )
        }
        // 切换不断开房间：文字与语音共用同一个 agent，切回文字时只是不再发布麦克风。
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

    fun clearError() {
        _state.update { it.copy(error = null) }
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
                        CompanionMessage(
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
                messages = st.messages + CompanionMessage(
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
                            messages = st.messages + CompanionMessage(
                                role = MessageRole.ASSISTANT,
                                text = event.text,
                                proactive = event.kind,
                            ),
                        )
                    }

                    is CompanionEvent.NeedsConfirmation -> _state.update { st ->
                        // 待确认动作不当作普通消息：它在界面上要变成一对按钮。
                        st.copy(
                            messages = st.messages + CompanionMessage(
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
        inboxJob?.cancel()
        viewModelScope.launch { transport.disconnect() }
    }

    companion object {
        const val DEFAULT_INBOX_POLL_MILLIS = 30_000L
    }
}
