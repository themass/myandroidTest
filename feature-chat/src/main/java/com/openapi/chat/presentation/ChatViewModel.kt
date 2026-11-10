package com.openapi.chat.presentation

import com.openapi.core.media.AudioPlayer
import com.openapi.asr.tts.DeviceSpeech
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
    private val history = HistoryActions(repository, _uiState, viewModelScope, isInFlight = { it in inFlight })
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
    fun onSend() {
        val state = _uiState.value
        if (!state.canSend) return
        val text = state.input.trim()
        val drafts = state.drafts
        // 正在编辑一条用户消息：提交的是编辑，而不是新消息（附件草稿不参与编辑）
        val editingId = state.editingMessageId
        if (editingId != null && drafts.isEmpty()) {
            submitEdit(editingId, text)
            return
        }
        _uiState.update { it.copy(input = "", drafts = emptyList()) }
        if (drafts.isEmpty()) {
            sendText(text)
            return
        }
        // 附件和文字组成一条消息：界面上一个气泡，保存一条记录
        val now = System.currentTimeMillis()
        val images = drafts.filter { it.kind == MessageKind.IMAGE }
            .map { ImageAttachment(path = it.staged.file.absolutePath) }
        val files = drafts.filter { it.kind == MessageKind.FILE }
            .map { FileAttachment(path = it.staged.file.absolutePath, name = it.staged.displayName, sizeBytes = it.staged.sizeBytes) }
        val message = ChatMessage(
            id = "local-user-$now",
            role = ChatRole.USER,
            text = text,
            createdAtMillis = now,
            kind = if (images.isNotEmpty()) MessageKind.IMAGE else MessageKind.FILE,
            images = images,
            files = files,
            delivery = DeliveryStatus.SENDING,
        )
        _uiState.update { it.copy(messages = it.messages + message) }
        attachmentJob = viewModelScope.launch {
            repository.saveMessage(repository.currentSessionId(), message)
            deliverAttachments(message)
        }
    }

    /** 纯文字消息：插入用户气泡，然后流式接收回复。 */
    private fun sendText(text: String) {
        if (text.isBlank() || _uiState.value.sending) return
        val now = System.currentTimeMillis()
        val user = ChatMessage(
            id = "local-user-$now",
            role = ChatRole.USER,
            text = text,
            createdAtMillis = now,
            delivery = DeliveryStatus.SENDING,
        )
        _uiState.update { it.copy(messages = it.messages + user) }
        streamReply(text, userMessageId = user.id, persistUser = true)
    }

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
    /** 附件发送也可以被「停止」打断：和流式回复一样显示生成中，停止后该消息标为可重试。 */
    private suspend fun deliverAttachments(message: ChatMessage) {
        inFlight += message.id
        _uiState.update { it.copy(streaming = true) }
        try {
            deliverAttachmentsBody(message)
        } finally {
            inFlight -= message.id
            _uiState.update { it.copy(streaming = streamer.isReplyActive()) }
        }
    }

    /** 正在本进程里发送的附件消息 id：刷新历史时不能把它们当成「中断的发送」。 */
    private val inFlight = HashSet<String>()

    /**
     * 历史页 + 本地还没同步的用户消息（附件等）。冷启动时，上次没发完的「发送中」消息说明进程被杀了，
     * 标成失败（红色重试），而不是消失或一直转圈。
     */
    private suspend fun mergePending(page: List<ChatMessage>, sessionId: Long): List<ChatMessage> =
        history.mergePending(page, sessionId)


    /** 把附件消息的发送结果写回本地记录，重启后状态一致。 */
    private suspend fun persistDelivery(message: ChatMessage, status: DeliveryStatus) {
        repository.saveMessage(repository.currentSessionId(), message.copy(delivery = status))
    }

    private suspend fun deliverAttachmentsBody(message: ChatMessage) {
        setDelivery(message.id, DeliveryStatus.SENDING)
        val placeholder = newPlaceholder()
        _uiState.update { it.copy(messages = it.messages + placeholder, sending = true, error = null) }
        val sessionId = repository.currentSessionId()
        val uploads = message.images.map { Triple(MessageKind.IMAGE, java.io.File(it.path), java.io.File(it.path).name) } +
            message.files.map { Triple(MessageKind.FILE, java.io.File(it.path), it.name) }

        val replies = mutableListOf<String>()
        var replyId: String? = null
        for ((kind, file, name) in uploads) {
            val reply = runCatching {
                kotlinx.coroutines.withTimeoutOrNull(ATTACHMENT_TIMEOUT_MS) {
                    repository.sendAttachment(sessionId, kind, file, name, placeholder.id)
                }
            }.getOrNull()
            if (reply == null) {
                val stopped = !kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]!!.isActive
                _uiState.update {
                    it.copy(
                        messages = it.messages.filterNot { m -> m.id == placeholder.id },
                        sending = false,
                        error = if (stopped) "已停止，消息未发出，可点重试" else "发送失败，请稍后重试",
                    )
                }
                setDelivery(message.id, DeliveryStatus.FAILED)
                persistDelivery(message, DeliveryStatus.FAILED)
                return
            }
            replyId = reply.id
            if (reply.text.isNotBlank()) replies += reply.text
        }
        setDelivery(message.id, DeliveryStatus.SENT)
        persistDelivery(message, DeliveryStatus.SENT)

        val combined = replies.joinToString("\n\n")
        if (combined.isBlank()) {
            _uiState.update { it.copy(messages = it.messages.filterNot { m -> m.id == placeholder.id }, sending = false) }
        } else {
            val assistant = ChatMessage(
                id = replyId ?: "assistant-${System.currentTimeMillis()}",
                role = ChatRole.ASSISTANT,
                text = combined,
                createdAtMillis = System.currentTimeMillis(),
            )
            repository.saveMessage(sessionId, assistant)
            _uiState.update { current ->
                current.copy(
                    messages = current.messages.map { m -> if (m.id == placeholder.id) assistant.copy(key = placeholder.key) else m },
                    sending = false,
                )
            }
        }
        // 附件不在对话历史接口里，这里不再从服务端整页刷新，否则附件气泡会被冲掉
        if (message.text.isNotBlank()) streamReply(message.text, userMessageId = null, persistUser = false, syncAfter = false)
    }

    /** 重新发送一条失败的用户消息（不重新插入气泡，也不重复保存）。 */
    fun onRetry(messageId: String) {
        val state = _uiState.value
        val message = state.messages.firstOrNull { it.id == messageId } ?: return
        if (state.sending || message.role != ChatRole.USER) return
        if (message.images.isNotEmpty() || message.files.isNotEmpty()) {
            attachmentJob = viewModelScope.launch { deliverAttachments(message) }
        } else if (message.text.isNotBlank()) {
            streamReply(message.text, userMessageId = message.id, persistUser = false)
        }
    }

    /**
     * 重新生成：与服务端一致，去掉最后一条用户消息之后的所有助手回复，然后重新回答最后一条用户消息。
     * 只对最后一轮有意义；[assistantId] 仅用于定位，实际删除范围由最后一条用户消息决定。
     */
    fun onRegenerate(assistantId: String) {
        val state = _uiState.value
        if (state.sending) return
        val lastUserIndex = state.messages.indexOfLast { it.role == ChatRole.USER }
        if (lastUserIndex < 0) return
        val lastUser = state.messages[lastUserIndex]
        val lastReply = state.messages.lastOrNull()?.takeIf { it.role == ChatRole.ASSISTANT }
        if (lastUser.images.isNotEmpty() || lastUser.files.isNotEmpty()) {
            // 带附件的消息不走 regenerate（附件不在服务端历史里），按原样重新上传：这种情况仍然截断后重发
            _uiState.update { it.copy(messages = state.messages.subList(0, lastUserIndex + 1).toList()) }
            attachmentJob = viewModelScope.launch { deliverAttachments(lastUser) }
        } else if (lastReply != null) {
            // 原位替换：最新回复的气泡留在原处，新内容流进同一个气泡
            streamReply(text = null, userMessageId = null, persistUser = false, regenerate = true, replaceKey = lastReply.key)
        } else {
            _uiState.update { it.copy(messages = state.messages.subList(0, lastUserIndex + 1).toList()) }
            streamReply(text = null, userMessageId = null, persistUser = false, regenerate = true)
        }
    }

    /** 进入编辑：把用户消息的文字放回输入框，发送时提交为编辑（而不是新消息）。 */
    fun onEditMessage(messageId: String) = composer.startEdit(messageId)


    /** 取消编辑：清空输入框并退出编辑状态。 */
    fun onCancelEdit() = composer.cancelEdit()


    /**
     * 提交编辑：服务端改写这条用户消息，并删除它之后的所有消息；本地同步丢弃后续气泡，
     * 然后用 regenerate 重新回答。
     */
    private fun submitEdit(messageId: String, text: String) {
        val state = _uiState.value
        val index = state.messages.indexOfFirst { it.id == messageId }
        if (index < 0) {
            _uiState.update { it.copy(editingMessageId = null) }
            return
        }
        if (messageId.toLongOrNull() == null) {
            _uiState.update { it.copy(error = "这条消息还没同步到服务端，稍后再编辑") }
            return
        }
        val edited = state.messages[index].copy(text = text)
        _uiState.update {
            it.copy(
                messages = state.messages.subList(0, index).toList() + edited,
                input = "",
                editingMessageId = null,
                error = null,
            )
        }
        viewModelScope.launch {
            val ok = repository.editMessage(state.sessionId, messageId, text)
            if (!ok) {
                _uiState.update { it.copy(error = "编辑失败，请稍后重试") }
                refresh()
                return@launch
            }
            streamReply(text = null, userMessageId = null, persistUser = false, regenerate = true)
        }
    }

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
    fun onAttachmentPicked(context: android.content.Context, kind: MessageKind, uri: android.net.Uri) {
        if (_uiState.value.sending) return
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { AttachmentStager.stage(context, kind, uri) }
            when (outcome) {
                is StageOutcome.Staged -> addDraft(kind, outcome.attachment)
                is StageOutcome.Rejected -> _uiState.update { it.copy(error = outcome.reason) }
                StageOutcome.Failed -> _uiState.update { it.copy(error = "读取附件失败，请重试") }
            }
        }
    }

    private fun addDraft(kind: MessageKind, staged: StagedAttachment) = composer.addDraft(kind, staged)


    fun onRemoveDraft(id: String) = composer.removeDraft(id)


    /** 相册多选或拍照：按选择顺序逐个放进草稿托盘。 */
    fun onImagesPicked(context: android.content.Context, uris: List<android.net.Uri>) {
        if (uris.isEmpty() || _uiState.value.sending) return
        viewModelScope.launch {
            for (uri in uris) {
                val outcome = withContext(Dispatchers.IO) { AttachmentStager.stage(context, MessageKind.IMAGE, uri) }
                when (outcome) {
                    is StageOutcome.Staged -> addDraft(MessageKind.IMAGE, outcome.attachment)
                    is StageOutcome.Rejected -> _uiState.update { it.copy(error = outcome.reason) }
                    StageOutcome.Failed -> _uiState.update { it.copy(error = "读取图片失败，请重试") }
                }
            }
        }
    }

    /**
     * 朗读助手回复：优先用设备自带中文语音（离线、免费）；设备没有中文语音或失败时，
     * 回退到服务端合成后播放。同一时间只朗读一条。
     */
    fun onSpeakMessage(context: android.content.Context, message: ChatMessage) {
        if (message.text.isBlank() || _uiState.value.speakingMessageId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(speakingMessageId = message.id) }
            val spokenOnDevice = runCatching { DeviceSpeech.speakChinese(context, message.text) }.getOrDefault(false)
            if (spokenOnDevice) {
                _uiState.update { it.copy(speakingMessageId = null) }
                return@launch
            }
            val uri = runCatching { repository.synthesizeSpeech(message.id, message.text) }.getOrNull()
            _uiState.update { it.copy(speakingMessageId = null) }
            if (uri == null) {
                _uiState.update { it.copy(error = "朗读失败，请稍后重试") }
            } else {
                AudioPlayer.play(message.id, uri)
            }
        }
    }

    fun dismissError() = composer.dismissError()


    // ------------------------------------------------------------------
    // 语音消息：录音 → 存成消息 → 转写 → 当作文本发给大模型。
    // ASR 通过 [SpeechToText] 注入（实现是 app 里的字节引擎封装），未注入时
    // 只保留语音气泡不转写，和之前的降级行为一致。
    // ------------------------------------------------------------------

    private var liveAsr: com.openapi.ks.myapp.translation.realtime.VolcengineStreamingAsrClient? = null

    /** 开始录音；[liveTranscript] 为 true 时同时开启实时识别（语音样式 B）。 */
    fun onVoiceRecordStart(context: android.content.Context, liveTranscript: Boolean) {
        if (_uiState.value.recording) return
        val started = VoiceRecorder.start(context)
        _uiState.update { it.copy(recording = started, partialText = "") }
        if (started && liveTranscript) startLiveTranscript(context)
    }

    /**
     * 样式 B：录音的同时把 PCM 送给火山流式识别，识别出的文字实时显示在输入栏上方。
     * 最终发送的文字仍然以松手后的后端识别结果为准。
     */
    private fun startLiveTranscript(context: android.content.Context) {
        val config = com.openapi.ks.myapp.translation.realtime.VolcengineAsrConfig.load(context)
        val client = com.openapi.ks.myapp.translation.realtime.VolcengineStreamingAsrClient(config)
        liveAsr = client
        client.connect(object : com.openapi.ks.myapp.translation.realtime.VolcengineStreamingAsrClient.Listener {
            override fun onReady() = Unit
            override fun onPartialText(text: String) {
                _uiState.update { it.copy(partialText = text) }
            }
            override fun onFailure(message: String) {
                liveAsr = null
            }
            override fun onClosed() = Unit
        })
        VoiceRecorder.pcmListener = { buffer, length ->
            // 录音线程会复用 buffer，发送前先拷贝一份
            client.sendPcm(buffer.copyOf(length), 0, length, false)
        }
    }

    private fun stopLiveTranscript() {
        VoiceRecorder.pcmListener = null
        liveAsr?.finish()
        liveAsr = null
    }

    /** 上滑取消手势触发；丢弃本次录音，不生成消息。 */
    fun onVoiceRecordCancel() {
        stopLiveTranscript()
        VoiceRecorder.cancel()
        _uiState.update { it.copy(recording = false, partialText = "") }
    }

    /**
     * 「转文字」：松手后不直接发送。录音文件保留，识别出的文字进入编辑态（[ChatUiState.voiceDraft]），
     * 用户可以改文字后发送文字、发送原语音，或取消（取消时删除录音文件）。
     */
    fun onVoiceRecordStopToText() {
        if (!_uiState.value.recording) return
        stopLiveTranscript()
        _uiState.update { it.copy(recording = false, partialText = "", transcribing = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { VoiceRecorder.stop() }
            val speech = ChatDependencies.speechToText()
            val text = if (result == null || speech == null) {
                null
            } else {
                runCatching { speech.transcribe(result.filePath) }.getOrNull()
            }
            _uiState.update { current ->
                current.copy(
                    transcribing = false,
                    voiceDraft = result?.let { VoiceDraft(it.filePath, it.durationSeconds, text.orEmpty()) },
                )
            }
        }
    }

    /** 编辑态：发送改好的文字（作为一条普通文字消息，并请求回复）。录音文件删除。 */
    fun onVoiceDraftSendText(text: String) {
        val draft = _uiState.value.voiceDraft ?: return
        discardDraftFile(draft)
        _uiState.update { it.copy(voiceDraft = null) }
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) sendText(trimmed)
    }

    /** 编辑态：只发送原语音（语音气泡，文字为识别结果，不请求回复）。 */
    fun onVoiceDraftSendAudio() {
        val draft = _uiState.value.voiceDraft ?: return
        _uiState.update { it.copy(voiceDraft = null) }
        viewModelScope.launch {
            val message = ChatMessage(
                id = "local-voice-${System.currentTimeMillis()}",
                role = ChatRole.USER,
                text = draft.text,
                createdAtMillis = System.currentTimeMillis(),
                kind = MessageKind.VOICE,
                voice = VoiceAttachment(uri = "file://${draft.filePath}", durationSeconds = draft.durationSeconds),
            )
            repository.saveMessage(repository.currentSessionId(), message)
            _uiState.update { it.copy(messages = it.messages + message) }
        }
    }

    /** 编辑态：取消，删除录音文件，回到普通输入。 */
    fun onVoiceDraftCancel() {
        val draft = _uiState.value.voiceDraft ?: return
        discardDraftFile(draft)
        _uiState.update { it.copy(voiceDraft = null) }
    }

    private fun discardDraftFile(draft: VoiceDraft) {
        runCatching { java.io.File(draft.filePath).delete() }
    }

    fun onVoiceRecordStop() {
        if (!_uiState.value.recording) return
        stopLiveTranscript()
        _uiState.update { it.copy(recording = false, partialText = "") }
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
            if (text.isNullOrBlank()) return@launch
            // 转写显示在语音气泡下方（同一条消息），并写回本地记录，重启后也能看到
            val transcribed = message.copy(text = text)
            repository.saveMessage(repository.currentSessionId(), transcribed)
            _uiState.update { current ->
                current.copy(messages = current.messages.map { if (it.id == message.id) transcribed else it })
            }
            // 语音气泡已经保存过：这里不能再让仓库保存用户消息。仓库保存的是纯文字行，会用同一个 chatId
            // 覆盖掉语音记录，语音就没了（重试路径同样是 persistUser = false）。
            streamReply(text, userMessageId = message.id, persistUser = false)
        }
    }
}
