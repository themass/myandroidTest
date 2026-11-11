package com.openapi.chat.presentation

import com.openapi.speech.audio.AudioPlayer
import com.openapi.speech.tts.DeviceSpeech
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openapi.chat.ChatDependencies
import com.openapi.chat.domain.model.ChatMessage
import com.openapi.chat.domain.model.FileAttachment
import com.openapi.chat.domain.model.ImageAttachment
import com.openapi.chat.domain.model.ChatRole
import com.openapi.chat.domain.model.HistoryPage
import com.openapi.chat.domain.model.ChatStreamEvent
import com.openapi.chat.domain.model.DeliveryStatus
import com.openapi.chat.domain.model.MessageKind
import com.openapi.chat.domain.model.VoiceAttachment
import com.openapi.chat.domain.usecase.LoadHistoryUseCase
import com.openapi.chat.domain.usecase.SendMessageUseCase
import com.openapi.speech.audio.VoiceRecorder
import com.openapi.feature.chat.roles.data.RolesDependencies
import com.openapi.feature.chat.roles.domain.SelectedRole
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

    private companion object {
        /** 流式文字的释放节拍：约 25 次/秒，既顺滑又不会让 Markdown 重解析太频繁。 */
        const val REVEAL_TICK_MS = 40L
        /** 单个附件上传+识别的超时：超时按失败处理，气泡显示重试图标，不再一直转圈。 */
        const val ATTACHMENT_TIMEOUT_MS = 120_000L
    }

    private val repository = ChatDependencies.require()
    private val loadHistory = LoadHistoryUseCase(repository)
    private val sendMessage = SendMessageUseCase(repository)
    private val roles = RolesDependencies.require()

    private val _uiState = MutableStateFlow(ChatUiState(selectedRole = roles.selected()))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val characterActions = CharacterActions(repository, roles, _uiState, viewModelScope)
    private val composer = ComposerActions(_uiState)
    private val streamer = ReplyStreamer(
        repository, _uiState, viewModelScope, sendMessage,
        attachmentActive = { attachmentJob?.isActive == true },
        reconcile = { fallbackReloadMessages() },
        syncSessions = { syncSessionsList() },
    )
    private val attachments = AttachmentSender(
        repository, _uiState, streamer,
        streamText = { t -> streamReply(t, userMessageId = null, persistUser = false, syncAfter = false) },
    )
    private val resend = ResendActions(
        repository, _uiState, viewModelScope, streamer,
        startAttachments = { m -> attachmentJob = viewModelScope.launch { deliverAttachments(m) } },
        refresh = { refresh() },
    )
    private val send = SendActions(
        repository, _uiState, viewModelScope, composer, streamer,
        submitEdit = { id, text -> submitEdit(id, text) },
        startAttachmentSend = { m -> attachmentJob = viewModelScope.launch { repository.saveMessage(repository.currentSessionId(), m); deliverAttachments(m) } },
    )
    private val history = HistoryActions(repository, _uiState, viewModelScope, isInFlight = { attachments.isInFlight(it) })
    private val feedback = FeedbackActions(_uiState)
    private val removal = MessageRemoval(repository, _uiState, viewModelScope, reconcile = { fallbackReloadMessages() })

    init {
        characterActions.load()
        // 会话和历史都以服务端为准：先同步会话列表（会决定当前会话），再拉最新一页历史
        viewModelScope.launch {
            repository.syncSessionsFromServer()
            refresh()
        }
    }

    /** 重新加载当前会话：服务端最新一页历史 + 会话列表（服务端不可达时回退到本地缓存）。 */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val page = runCatching { loadHistory() }.getOrElse { HistoryPage(emptyList(), hasMore = false) }
            val sessionId = repository.currentSessionId()
            val merged = mergePending(page.messages, sessionId)
            val sessions = withContext(Dispatchers.IO) { repository.sessions() }
            _uiState.update {
                it.copy(
                    messages = merged,
                    hasMoreHistory = page.hasMore,
                    loadingOlder = false,
                    editingMessageId = null,
                    loading = false,
                    sessionId = sessionId,
                    // 每个会话记住自己的角色；没选过的会话沿用最近一次选的角色
                    selectedRole = roles.selectedFor(sessionId) ?: roles.selected(),
                    sessions = sessions,
                )
            }
        }
    }

    fun onSessionsVisibleChange(visible: Boolean) {
        _uiState.update { it.copy(sessionsVisible = visible) }
        // 打开会话列表时才向服务端要一次列表（updatedAt、别的设备上的新会话）
        if (visible) viewModelScope.launch { runCatching { syncSessionsList() } }
    }

    private val sessionActions = SessionActions(repository, _uiState, viewModelScope, ::refresh)

    fun onSessionSelected(sessionId: Long) = sessionActions.select(sessionId)

    fun onCreateSession(name: String) = sessionActions.create(name)

    fun onDeleteSession(sessionId: Long) = sessionActions.delete(sessionId)

    fun onRenameSession(sessionId: Long, name: String) = sessionActions.rename(sessionId, name)

    

    /** 选了旧的人设（或默认助手）就把目录角色清掉：两套选择互斥，避免界面显示的和实际发出去的不一致。 */
    fun onCharacterSelected(characterId: Int?) = characterActions.selectCharacter(characterId)


    fun onRoleSelected(role: SelectedRole) = characterActions.selectRole(role)


    fun onRolePickerVisibleChange(visible: Boolean) = characterActions.setRolePickerVisible(visible)


    /**
     * 创建一个自定义角色并立即选中它——用户上传角色的目的就是马上用它聊天，
     * 不需要创建完再去下拉菜单里找一遍。
     */
    fun onCreateCharacter(title: String, content: String) = characterActions.create(title, content)


    fun onUpdateCharacter(characterId: Int, title: String, content: String) =
        characterActions.update(characterId, title, content)


    fun onDeleteCharacter(characterId: Int) = characterActions.delete(characterId)


    fun onInputChange(text: String) = composer.setInput(text)


    /**
     * 发送一条文本消息，流式接收回复（打字机效果）。
     *
     * 先插一条 pending 的占位气泡；每收到一段 [ChatStreamEvent.Delta] 就把文字追加到
     * 这条气泡上；[ChatStreamEvent.Done] 到达后取消 pending 状态；出错则整条占位消息撤回，
     * 和原来非流式版本的失败处理保持一致（不在界面上留一条"发送失败"的半成品）。
     */
    fun onSend() = send.onSend()

    /** 纯文字消息：插入用户气泡，然后流式接收回复。 */
    private fun sendText(text: String) = send.sendText(text)

    private fun streamReply(
        text: String?,
        userMessageId: String?,
        persistUser: Boolean,
        regenerate: Boolean = false,
        syncAfter: Boolean = true,
        replaceKey: String? = null,
    ) = streamer.stream(text, userMessageId, persistUser, regenerate, syncAfter, replaceKey)

    private fun updateByKey(key: String, transform: (ChatMessage) -> ChatMessage) = streamer.updateByKey(key, transform)

    private fun newPlaceholder(): ChatMessage = streamer.newPlaceholder()

    private fun setDelivery(messageId: String, status: DeliveryStatus) = streamer.setDelivery(messageId, status)

    /** 停止生成：取消网络请求，已经显示和已收到的内容都保留。 */
    fun onStopGenerating() {
        streamer.cancel()
        attachmentJob?.cancel()
    }

    /** 附件发送任务。停止按钮要能同时停掉两者，且两者互不覆盖。 */
    private var attachmentJob: kotlinx.coroutines.Job? = null

    /**
     * 发送带附件的用户消息：逐个上传附件，各自的识别结果合并成一条助手回复；
     * 有配文时再把配文走文字流（用户消息已保存过，不再重复保存）。
     */
    /**
     * 历史页 + 本地还没同步的用户消息（附件等）。冷启动时，上次没发完的「发送中」消息说明进程被杀了，
     * 标成失败（红色重试），而不是消失或一直转圈。
     */
    private suspend fun deliverAttachments(message: ChatMessage) = attachments.deliver(message)

    private suspend fun mergePending(page: List<ChatMessage>, sessionId: Long): List<ChatMessage> =
        history.mergePending(page, sessionId)


    /** 重新发送一条失败的用户消息（不重新插入气泡，也不重复保存）。 */
    fun onRetry(messageId: String) = resend.retry(messageId)

    /** 重新生成：去掉最后一条用户消息之后的助手回复，然后重新回答。 */
    fun onRegenerate(assistantId: String) = resend.regenerate(assistantId)

    /** 进入编辑：把用户消息的文字放回输入框，发送时提交为编辑（而不是新消息）。 */
    fun onEditMessage(messageId: String) = composer.startEdit(messageId)


    /** 取消编辑：清空输入框并退出编辑状态。 */
    fun onCancelEdit() = composer.cancelEdit()


    private fun submitEdit(messageId: String, text: String) = resend.submitEdit(messageId, text)

    /** 删除一条消息：服务端删除成功后再从界面移除；只存在于本地的附件气泡直接移除。 */
    fun onDeleteMessage(messageId: String) = removal.deleteOne(messageId)


    /**
     * 清空当前对话的消息，保留会话本身。服务端还没有「清空消息」接口，所以先分页拉出全部消息 id，
     * 再逐条删除（每条走服务端 + 本地缓存）。任一条失败会保留它并提示数量。
     */
    fun onClearConversation() = removal.clearConversation()


    /**
     * 批量删除（多选）：逐条调用单条删除（服务端 + 本地缓存），成功的从列表移除；
     * 没有服务端 id 的本地消息只从列表移除。失败的保留，并提示数量。
     */
    fun onDeleteMessages(messageIds: List<String>) = removal.deleteMany(messageIds)


    /** 加载更早的一页历史（按最早一条的服务端 id 作为 beforeId）。 */
    fun onLoadOlderMessages() = history.loadOlder()


    /** 从服务端同步会话列表（新会话、updatedAt、别的设备上的改动），并刷新界面里的列表。 */
    private suspend fun syncSessionsList() {
        repository.syncSessionsFromServer()
        val sessionId = repository.currentSessionId()
        val sessions = withContext(Dispatchers.IO) { repository.sessions() }
        _uiState.update { it.copy(sessionId = sessionId, sessions = sessions) }
    }

    /**
     * 兜底：只在拿不到正式消息 id 时（失败、没有 done 帧、空回复、停止）拉一页最新历史。
     * 正在发送时不替换消息列表，避免盖掉还在流式输出的气泡；服务端不可达时保留现状。
     */
    private suspend fun fallbackReloadMessages() {
        val page = runCatching { loadHistory() }.getOrNull() ?: return
        val sessionId = repository.currentSessionId()
        _uiState.update { cur ->
            if (cur.sending) {
                cur.copy(sessionId = sessionId)
            } else {
                // 服务端历史里没有「失败」这个状态：所有本地标成失败的用户消息，重新加载后都要保留失败标记（可重试），
                // 不能只保留最后一条。匹配规则：文字相同，且创建时间相差不超过 2 分钟（服务端分配的 id 与本地不同）。
                cur.copy(
                    messages = HistoryMerge.merge(cur.messages, page.messages),
                    hasMoreHistory = page.hasMore,
                    sessionId = sessionId,
                )
            }
        }
    }

    /** 点赞切换，返回切换后是否为「已赞」。点赞会取消点踩。 */
    fun onToggleLike(messageId: String): Boolean = feedback.toggleLike(messageId)


    /** 点踩并提交反馈后调用。点踩会取消点赞。 */
    fun onDisliked(messageId: String) = feedback.disliked(messageId)


    /**
     * 选中附件后的入口：图片和文件都先放进输入栏上方的草稿托盘，发送时和文字组成一条消息。
     */
    fun onAttachmentPicked(context: android.content.Context, kind: MessageKind, uri: android.net.Uri) =
        send.attachmentPicked(context, kind, uri)

    private fun addDraft(kind: MessageKind, staged: StagedAttachment) = composer.addDraft(kind, staged)


    fun onRemoveDraft(id: String) = composer.removeDraft(id)


    /** 相册多选或拍照：按选择顺序逐个放进草稿托盘。 */
    fun onImagesPicked(context: android.content.Context, uris: List<android.net.Uri>) =
        send.imagesPicked(context, uris)

    /**
     * 朗读助手回复：优先用设备自带中文语音（离线、免费）；设备没有中文语音或失败时，
     * 回退到服务端合成后播放。同一时间只朗读一条。
     */
    fun onSpeakMessage(context: android.content.Context, message: ChatMessage) = send.speak(context, message)

    fun dismissError() = composer.dismissError()


    // 语音消息：录音、转文字、发送语音或文字，逻辑在 VoiceActions。
    private val voice = VoiceActions(
        repository = repository,
        state = _uiState,
        scope = viewModelScope,
        sendText = { text -> sendText(text) },
        streamReply = { text, userMessageId, persistUser -> streamReply(text, userMessageId, persistUser) },
    )

    fun onVoiceRecordStart(context: android.content.Context, liveTranscript: Boolean) = voice.start(context, liveTranscript)

    fun onVoiceRecordCancel() = voice.cancel()

    fun onVoiceRecordStopToText() = voice.stopToText()

    fun onVoiceDraftSendText(text: String) = voice.draftSendText(text)

    fun onVoiceDraftSendAudio() = voice.draftSendAudio()

    fun onVoiceDraftCancel() = voice.draftCancel()

    fun onVoiceRecordStop() = voice.stopAndSend()
}
