package com.openapi.chat.presentation

import com.openapi.core.media.AudioPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openapi.chat.ChatDependencies
import com.openapi.chat.domain.model.ChatMessage
import com.openapi.chat.domain.model.FileAttachment
import com.openapi.chat.domain.model.ImageAttachment
import com.openapi.chat.domain.model.ChatRole
import com.openapi.chat.domain.model.ChatStreamEvent
import com.openapi.chat.domain.model.MessageKind
import com.openapi.chat.domain.model.VoiceAttachment
import com.openapi.chat.domain.usecase.LoadHistoryUseCase
import com.openapi.chat.domain.usecase.SendMessageUseCase
import com.openapi.core.media.VoiceRecorder
import com.openapi.roles.data.RolesDependencies
import com.openapi.roles.domain.SelectedRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel : ViewModel() {

    private val repository = ChatDependencies.require()
    private val loadHistory = LoadHistoryUseCase(repository)
    private val sendMessage = SendMessageUseCase(repository)
    private val roles = RolesDependencies.require()

    private val _uiState = MutableStateFlow(ChatUiState(selectedRole = roles.selected()))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        refresh()
        loadCharacters()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val history = runCatching { loadHistory() }.getOrElse { emptyList() }
            _uiState.update {
                it.copy(
                    messages = history,
                    loading = false,
                    sessionId = repository.currentSessionId(),
                )
            }
        }
    }

    private fun loadCharacters() {
        viewModelScope.launch {
            val characters = runCatching { repository.loadCharacters() }.getOrElse { emptyList() }
            _uiState.update {
                it.copy(
                    characters = characters,
                    selectedCharacterId = repository.selectedCharacterId(),
                )
            }
        }
    }

    /** 选了旧的人设（或默认助手）就把目录角色清掉：两套选择互斥，避免界面显示的和实际发出去的不一致。 */
    fun onCharacterSelected(characterId: Int?) {
        repository.setSelectedCharacterId(characterId)
        roles.setSelected(null)
        _uiState.update { it.copy(selectedCharacterId = characterId, selectedRole = null, rolePickerVisible = false) }
    }

    fun onRoleSelected(role: SelectedRole) {
        roles.setSelected(role)
        repository.setSelectedCharacterId(null)
        _uiState.update { it.copy(selectedRole = role, selectedCharacterId = null, rolePickerVisible = false) }
    }

    fun onRolePickerVisibleChange(visible: Boolean) {
        _uiState.update { it.copy(rolePickerVisible = visible) }
    }

    /**
     * 创建一个自定义角色并立即选中它——用户上传角色的目的就是马上用它聊天，
     * 不需要创建完再去下拉菜单里找一遍。
     */
    fun onCreateCharacter(title: String, content: String) {
        viewModelScope.launch {
            val created = runCatching { repository.createCharacter(title, content, null) }.getOrNull()
            if (created == null) {
                _uiState.update { it.copy(error = "创建角色失败，请稍后重试") }
                return@launch
            }
            repository.setSelectedCharacterId(created.id)
            roles.setSelected(null)
            _uiState.update {
                it.copy(
                    characters = it.characters + created,
                    selectedCharacterId = created.id,
                    selectedRole = null,
                )
            }
        }
    }

    fun onUpdateCharacter(characterId: Int, title: String, content: String) {
        viewModelScope.launch {
            val ok = runCatching { repository.updateCharacter(characterId, title, content, null) }.getOrDefault(false)
            if (!ok) {
                _uiState.update { it.copy(error = "更新角色失败，请稍后重试") }
                return@launch
            }
            _uiState.update { state ->
                state.copy(
                    characters = state.characters.map {
                        if (it.id == characterId) {
                            it.copy(title = title, preview = content.take(40), content = content)
                        } else {
                            it
                        }
                    },
                )
            }
        }
    }

    fun onDeleteCharacter(characterId: Int) {
        viewModelScope.launch {
            val ok = runCatching { repository.deleteCharacter(characterId) }.getOrDefault(false)
            if (!ok) {
                _uiState.update { it.copy(error = "删除角色失败，请稍后重试") }
                return@launch
            }
            val wasSelected = _uiState.value.selectedCharacterId == characterId
            _uiState.update { state ->
                state.copy(
                    characters = state.characters.filterNot { it.id == characterId },
                    // 删的正好是当前选中的角色，回退到默认助手，不留一个指向空角色的选择
                    selectedCharacterId = if (wasSelected) null else state.selectedCharacterId,
                )
            }
            if (wasSelected) repository.setSelectedCharacterId(null)
        }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    /**
     * 发送一条文本消息，流式接收回复（打字机效果）。
     *
     * 先插一条 pending 的占位气泡；每收到一段 [ChatStreamEvent.Delta] 就把文字追加到
     * 这条气泡上；[ChatStreamEvent.Done] 到达后取消 pending 状态；出错则整条占位消息撤回，
     * 和原来非流式版本的失败处理保持一致（不在界面上留一条"发送失败"的半成品）。
     */
    fun onSend() {
        val state = _uiState.value
        val text = state.input.trim()
        if (text.isEmpty() || state.sending) return
        _uiState.update { it.copy(input = "") }
        sendText(text)
    }

    /**
     * 把一段用户文本发出去并流式接收回复。文本来源可以是输入框，也可以是 ASR 转写结果，
     * 所以发送逻辑收在这里一份，避免两条路径行为不一致。
     */
    private fun sendText(text: String) {
        val state = _uiState.value
        if (text.isBlank() || state.sending) return

        val now = System.currentTimeMillis()
        val userMessage = ChatMessage(
            id = "local-user-$now",
            role = ChatRole.USER,
            text = text,
            createdAtMillis = now,
        )
        val placeholder = ChatMessage(
            id = "local-pending-$now",
            role = ChatRole.ASSISTANT,
            text = "",
            createdAtMillis = now,
            pending = true,
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage + placeholder,
                sending = true,
                error = null,
            )
        }

        viewModelScope.launch {
            sendMessage(text, state.selectedCharacterId, state.selectedRole?.id)
                .catch { error ->
                    _uiState.update { current ->
                        current.copy(
                            messages = current.messages.filterNot { it.id == placeholder.id },
                            error = error.message,
                        )
                    }
                }
                .onCompletion {
                    _uiState.update { it.copy(sending = false) }
                }
                .collect { event ->
                    when (event) {
                        is ChatStreamEvent.Delta -> _uiState.update { current ->
                            current.copy(
                                messages = current.messages.map {
                                    if (it.id == placeholder.id) it.copy(text = it.text + event.text) else it
                                },
                            )
                        }

                        is ChatStreamEvent.Done -> _uiState.update { current ->
                            current.copy(
                                messages = current.messages.map {
                                    if (it.id == placeholder.id) {
                                        it.copy(id = event.messageId, text = event.fullText, pending = false)
                                    } else {
                                        it
                                    }
                                },
                            )
                        }

                        is ChatStreamEvent.Error -> _uiState.update { current ->
                            current.copy(
                                messages = current.messages.filterNot { it.id == placeholder.id },
                                error = event.message,
                            )
                        }
                    }
                }
        }
    }

    /**
     * 发送一个图片或文件附件。流程与语音一致：先把附件存成消息并显示占位，
     * 上传完成后用回复替换占位；失败则撤回占位并提示。
     */
    fun onAttachmentPicked(context: android.content.Context, kind: MessageKind, uri: android.net.Uri) {
        if (_uiState.value.sending) return
        viewModelScope.launch {
            val staged = withContext(Dispatchers.IO) { AttachmentStager.stage(context, kind, uri) }
            if (staged == null) {
                _uiState.update { it.copy(error = "读取附件失败，请重试") }
                return@launch
            }

            val now = System.currentTimeMillis()
            val userMessage = ChatMessage(
                id = "local-att-$now",
                role = ChatRole.USER,
                text = "",
                createdAtMillis = now,
                kind = kind,
                image = if (kind == MessageKind.IMAGE) ImageAttachment(path = staged.file.absolutePath) else null,
                file = if (kind == MessageKind.FILE) {
                    FileAttachment(path = staged.file.absolutePath, name = staged.displayName, sizeBytes = staged.sizeBytes)
                } else {
                    null
                },
            )
            val placeholder = ChatMessage(
                id = "local-pending-$now",
                role = ChatRole.ASSISTANT,
                text = "",
                createdAtMillis = now,
                pending = true,
            )

            _uiState.update {
                it.copy(messages = it.messages + userMessage + placeholder, sending = true, error = null)
            }
            val sessionId = repository.currentSessionId()
            repository.saveMessage(sessionId, userMessage)

            val reply = runCatching {
                repository.sendAttachment(sessionId, kind, staged.file, staged.displayName, placeholder.id)
            }.getOrNull()

            if (reply == null) {
                _uiState.update {
                    it.copy(
                        messages = it.messages.filterNot { m -> m.id == placeholder.id },
                        sending = false,
                        error = "发送失败，请稍后重试",
                    )
                }
                return@launch
            }

            val assistant = ChatMessage(
                id = reply.id,
                role = ChatRole.ASSISTANT,
                text = reply.text,
                createdAtMillis = System.currentTimeMillis(),
            )
            if (reply.text.isNotBlank()) repository.saveMessage(sessionId, assistant)
            _uiState.update { current ->
                current.copy(
                    messages = current.messages.mapNotNull { m ->
                        when {
                            m.id != placeholder.id -> m
                            reply.text.isBlank() -> null
                            else -> assistant
                        }
                    },
                    sending = false,
                )
            }
        }
    }

    /** 朗读助手回复：服务端合成语音后播放；同一时间只合成一条。 */
    fun onSpeakMessage(message: ChatMessage) {
        if (message.text.isBlank() || _uiState.value.speakingMessageId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(speakingMessageId = message.id) }
            val uri = runCatching { repository.synthesizeSpeech(message.id, message.text) }.getOrNull()
            _uiState.update { it.copy(speakingMessageId = null) }
            if (uri == null) {
                _uiState.update { it.copy(error = "朗读失败，请稍后重试") }
            } else {
                AudioPlayer.play(message.id, uri)
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    // ------------------------------------------------------------------
    // 语音消息：录音 → 存成消息 → 转写 → 当作文本发给大模型。
    // ASR 通过 [SpeechToText] 注入（实现是 app 里的字节引擎封装），未注入时
    // 只保留语音气泡不转写，和之前的降级行为一致。
    // ------------------------------------------------------------------

    fun onVoiceRecordStart(context: android.content.Context) {
        if (_uiState.value.recording) return
        val started = VoiceRecorder.start(context)
        _uiState.update { it.copy(recording = started) }
    }

    /** 上滑取消手势触发；丢弃本次录音，不生成消息。 */
    fun onVoiceRecordCancel() {
        VoiceRecorder.cancel()
        _uiState.update { it.copy(recording = false) }
    }

    fun onVoiceRecordStop() {
        if (!_uiState.value.recording) return
        _uiState.update { it.copy(recording = false) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { VoiceRecorder.stop() } ?: return@launch
            val message = ChatMessage(
                id = "local-voice-${System.currentTimeMillis()}",
                role = ChatRole.USER,
                text = "",
                createdAtMillis = System.currentTimeMillis(),
                kind = MessageKind.VOICE,
                voice = VoiceAttachment(uri = "file://${result.filePath}", durationSeconds = result.durationSeconds),
            )
            repository.saveMessage(repository.currentSessionId(), message)
            _uiState.update { it.copy(messages = it.messages + message) }

            val speech = ChatDependencies.speechToText() ?: return@launch
            _uiState.update { it.copy(transcribing = true) }
            val text = runCatching { speech.transcribe(result.filePath) }.getOrNull()
            _uiState.update { it.copy(transcribing = false) }

            // 转写失败就停在这里：语音气泡已经发出去了，不拿空串去问大模型。
            if (!text.isNullOrBlank()) sendText(text)
        }
    }
}
