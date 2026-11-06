package com.openapi.ks.myapp.companion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 角色扮演界面的状态持有者。
 *
 * 房间连接藏在 [CompanionTransport] 后面，默认是离线替身，所以这个界面在没接
 * LiveKit、没后端的情况下也能完整走一遍：发消息、看流式回复、看形象切状态。
 */
class CompanionViewModel(
    private val api: CompanionApi? = null,
    private val transport: CompanionTransport = FakeCompanionTransport(),
) : ViewModel() {

    private val _state = MutableStateFlow(CompanionUiState())
    val state: StateFlow<CompanionUiState> = _state.asStateFlow()

    private var characterId: String? = null
    private var collectJob: Job? = null

    /**
     * 没有 [CompanionApi] 时直接连替身，用于预览与离线调试。
     */
    fun start(characterId: String) {
        if (this.characterId == characterId && collectJob != null) return
        this.characterId = characterId
        _state.update { it.copy(connection = CompanionConnection.CONNECTING, error = null) }
        viewModelScope.launch {
            try {
                val session = api?.createSession(characterId) ?: offlineSession(characterId)
                transport.connect(session)
                _state.update { it.copy(connection = CompanionConnection.CONNECTED) }
                listen()
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

    /** 最近一条形象事件，界面据此驱动渲染器。 */
    private var pendingAvatar: String? = null

    fun consumePendingAvatar(): String? = pendingAvatar.also { pendingAvatar = null }

    fun clearError() {
        _state.update { it.copy(error = null) }
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

    /** 用户确认一个待执行动作。 */
    fun confirmAction(confirmationId: String) {
        transport.confirm(confirmationId, accepted = true)
        removeConfirmation(confirmationId)
    }

    fun cancelAction(confirmationId: String) {
        transport.confirm(confirmationId, accepted = false)
        removeConfirmation(confirmationId)
    }

    fun dismissIncomingCall() {
        _state.update { it.copy(incomingCall = null) }
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
        viewModelScope.launch { transport.disconnect() }
    }
}
