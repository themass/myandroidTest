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
import com.openapi.chat.domain.usecase.DeleteMessageUseCase
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
    private val deleteMessage = DeleteMessageUseCase(repository)
    private val roles = RolesDependencies.require()

    private val _uiState = MutableStateFlow(ChatUiState(selectedRole = roles.selected()))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        loadCharacters()
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
            val sessions = withContext(Dispatchers.IO) { repository.sessions() }
            _uiState.update {
                it.copy(
                    messages = page.messages,
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

    private suspend fun reloadSessions() {
        val sessions = withContext(Dispatchers.IO) { repository.sessions() }
        _uiState.update { it.copy(sessions = sessions) }
    }

    fun onSessionsVisibleChange(visible: Boolean) {
        _uiState.update { it.copy(sessionsVisible = visible) }
        // 打开会话列表时才向服务端要一次列表（updatedAt、别的设备上的新会话）
        if (visible) viewModelScope.launch { runCatching { syncSessionsList() } }
    }

    /** 切换会话：写入当前会话 id，然后重新加载这个会话的历史。 */
    fun onSessionSelected(sessionId: Long) {
        if (sessionId == _uiState.value.sessionId) {
            _uiState.update { it.copy(sessionsVisible = false) }
            return
        }
        repository.setCurrentSessionId(sessionId)
        _uiState.update { it.copy(sessionsVisible = false) }
        refresh()
    }

    /** 新建会话并立即切过去。 */
    fun onCreateSession(name: String) {
        viewModelScope.launch {
            val created = withContext(Dispatchers.IO) { repository.createSession(name) }
            if (created == null) {
                _uiState.update { it.copy(error = "创建失败：名称不能为空、不能与已有会话重名，或网络异常") }
                return@launch
            }
            repository.setCurrentSessionId(created.id)
            _uiState.update { it.copy(sessionsVisible = false) }
            refresh()
        }
    }

    /** 删除会话（允许删空，服务端会在下次拉列表时自动新建一个）。删的是当前会话时，refresh 会切换。 */
    fun onDeleteSession(sessionId: Long) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { repository.deleteSession(sessionId) }
            if (!ok) {
                _uiState.update { it.copy(error = "删除失败，请稍后重试") }
                return@launch
            }
            refresh()
        }
    }

    /** 重命名会话（服务端）；成功后刷新会话列表。 */
    fun onRenameSession(sessionId: Long, name: String) {
        viewModelScope.launch {
            val renamed = repository.renameSession(sessionId, name)
            if (renamed == null) {
                _uiState.update { it.copy(error = "重命名失败：名称不能为空，或网络异常") }
                return@launch
            }
            reloadSessions()
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
        val sessionId = repository.currentSessionId()
        repository.setSelectedCharacterId(characterId)
        roles.setSelected(null)
        roles.setSelectedFor(sessionId, null)
        _uiState.update { it.copy(selectedCharacterId = characterId, selectedRole = null, rolePickerVisible = false) }
    }

    fun onRoleSelected(role: SelectedRole) {
        val sessionId = repository.currentSessionId()
        roles.setSelected(role)
        roles.setSelectedFor(sessionId, role)
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
            roles.setSelectedFor(repository.currentSessionId(), null)
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
    private fun streamReply(
        text: String?,
        userMessageId: String?,
        persistUser: Boolean,
        regenerate: Boolean = false,
        syncAfter: Boolean = true,
        /** 重新生成时：复用这条助手消息的气泡（同一个 key、同一个位置），而不是新增一条。 */
        replaceKey: String? = null,
    ) {
        val state = _uiState.value
        // 本轮是否会新建会话（请求时还没有当前会话）：结束后要同步会话列表
        val startsNewSession = repository.currentSessionId() <= 0L
        val existing = replaceKey?.let { key -> state.messages.firstOrNull { it.key == key } }
        val placeholder = existing?.copy(text = "", pending = true) ?: newPlaceholder()
        _uiState.update { current ->
            val messages = if (existing != null) {
                current.messages.map { if (it.key == existing.key) placeholder else it }
            } else {
                current.messages + placeholder
            }
            current.copy(messages = messages, sending = true, streaming = true, error = null)
        }
        userMessageId?.let { setDelivery(it, DeliveryStatus.SENDING) }

        replyJob = viewModelScope.launch {
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
                        val cut = safeCut(buffer, revealCount(buffer.length, ended))
                        val piece = buffer.substring(0, cut)
                        buffer.delete(0, cut)
                        updateByKey(placeholder.key) { it.copy(text = it.text + piece) }
                    } else if (ended) {
                        break
                    }
                }
            }

            try {
                val events = if (regenerate) {
                    repository.regenerateStream(repository.currentSessionId(), state.selectedCharacterId, state.selectedRole?.id)
                } else {
                    sendMessage(
                        text.orEmpty(),
                        state.selectedCharacterId,
                        state.selectedRole?.id,
                        persistUser,
                        clientMessageId = userMessageId,
                    )
                }
                events.collect { event ->
                        when (event) {
                            is ChatStreamEvent.Delta -> buffer.append(event.text)
                            is ChatStreamEvent.Done -> {
                                serverId = event.assistantId
                                serverUserId = event.userId
                                fullText = event.fullText
                            }
                            is ChatStreamEvent.Error -> {
                                failed = true
                                errorUserId = event.userMessageId
                                _uiState.update { it.copy(error = event.message) }
                            }
                        }
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 用户点了停止：保留已经生成的内容，不当作失败
                reveal.cancel()
                val replied = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    finishStopped(placeholder.key, buffer.toString())
                }
                // 停止时一个字都没收到：用户消息标成未发出（可点重试），不当作已发送
                _uiState.update {
                    it.copy(
                        sending = false,
                        streaming = attachmentJob?.isActive == true,
                        error = if (replied) it.error else "已停止，消息未发出，可点重试",
                    )
                }
                userMessageId?.let { setDelivery(it, if (replied) DeliveryStatus.SENT else DeliveryStatus.FAILED) }
                // 服务端会保存已生成的部分，但这里拿不到正式 id：兜底拉一页历史
                if (syncAfter) viewModelScope.launch { fallbackReloadMessages() }
                if (startsNewSession) viewModelScope.launch { syncSessionsList() }
                throw e
            } catch (e: Exception) {
                // 记日志：文字发送偶发失败时，能从 logcat 看到真实原因（之前无法复现原因）
                android.util.Log.w("ChatViewModel", "stream reply failed", e)
                failed = true
                _uiState.update { it.copy(error = e.message) }
            }

            ended = true
            reveal.join()

            // 流正常结束但一个字都没有：撤掉占位气泡，给提示，用户消息标成可重试
            val emptyReply = !failed && fullText.isNullOrEmpty() && buffer.isEmpty()
            _uiState.update { current ->
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
                    streaming = attachmentJob?.isActive == true,
                    error = when {
                        emptyReply -> "没有收到回复，请重试"
                        failed -> current.error ?: "回复失败，请重试"
                        else -> current.error
                    },
                )
            }
            // 流结束但没有 done 帧（服务端中途断开、只给了部分文字）：不能当成已完整收到，用户消息标成失败可重试
            val truncated = !failed && !emptyReply && serverId == null && serverUserId == null && userMessageId != null
            if (truncated) {
                _uiState.update { it.copy(error = "回复没有完整收到，请重试") }
            }
            userMessageId?.let { setDelivery(serverUserId ?: errorUserId ?: it, if (failed || emptyReply || truncated) DeliveryStatus.FAILED else DeliveryStatus.SENT) }
            // 正常路径靠 done 帧里的正式 id，不再每轮拉历史；只有缺 id（失败、没有 done 帧、空回复）时兜底拉一页
            if (syncAfter && (failed || serverId == null)) fallbackReloadMessages()
            if (startsNewSession) syncSessionsList()
        }
    }

    /** 停止生成：取消网络请求，已经显示和已收到的内容都保留。 */
    fun onStopGenerating() {
        replyJob?.cancel()
        attachmentJob?.cancel()
    }

    /** 返回是否保留了助手内容（false = 一个字都没收到，占位气泡已撤掉）。 */
    private suspend fun finishStopped(key: String, bufferedText: String): Boolean {
        var kept: ChatMessage? = null
        _uiState.update { current ->
            current.copy(
                messages = current.messages.mapNotNull { m ->
                    if (m.key != key) {
                        m
                    } else {
                        val merged = m.text + bufferedText
                        if (merged.isEmpty()) null else m.copy(text = merged, pending = false).also { kept = it }
                    }
                },
            )
        }
        kept?.let { repository.saveMessage(repository.currentSessionId(), it) }
        return kept != null
    }

    private fun updateByKey(key: String, transform: (ChatMessage) -> ChatMessage) {
        _uiState.update { current ->
            current.copy(messages = current.messages.map { if (it.key == key) transform(it) else it })
        }
    }

    /** 本拍释放多少字：积压越多越快（上限 48），流结束后加速排空。 */
    private fun revealCount(pending: Int, ended: Boolean): Int {
        val base = (pending / 8).coerceIn(1, 48)
        return if (ended) maxOf(base, pending / 3) else base
    }

    /** 不要把一个 emoji 等代理对从中间切开。 */
    private fun safeCut(text: CharSequence, wanted: Int): Int {
        var cut = wanted.coerceAtMost(text.length)
        if (cut in 1 until text.length && Character.isHighSurrogate(text[cut - 1])) cut += 1
        return cut
    }

    /** 文字回复的流式任务。 */
    private var replyJob: kotlinx.coroutines.Job? = null
    /** 附件发送任务。停止按钮要能同时停掉两者，且两者互不覆盖。 */
    private var attachmentJob: kotlinx.coroutines.Job? = null

    /**
     * 发送带附件的用户消息：逐个上传附件，各自的识别结果合并成一条助手回复；
     * 有配文时再把配文走文字流（用户消息已保存过，不再重复保存）。
     */
    /** 附件发送也可以被「停止」打断：和流式回复一样显示生成中，停止后该消息标为可重试。 */
    private suspend fun deliverAttachments(message: ChatMessage) {
        _uiState.update { it.copy(streaming = true) }
        try {
            deliverAttachmentsBody(message)
        } finally {
            _uiState.update { it.copy(streaming = replyJob?.isActive == true) }
        }
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
                return
            }
            replyId = reply.id
            if (reply.text.isNotBlank()) replies += reply.text
        }
        setDelivery(message.id, DeliveryStatus.SENT)

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

    private fun newPlaceholder(): ChatMessage {
        val now = System.currentTimeMillis()
        return ChatMessage(
            id = "local-pending-$now",
            role = ChatRole.ASSISTANT,
            text = "",
            createdAtMillis = now,
            pending = true,
        )
    }

    private fun setDelivery(messageId: String, status: DeliveryStatus) {
        _uiState.update { current ->
            current.copy(messages = current.messages.map { if (it.id == messageId) it.copy(delivery = status) else it })
        }
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
    fun onEditMessage(messageId: String) {
        val message = _uiState.value.messages.firstOrNull { it.id == messageId } ?: return
        if (message.role != ChatRole.USER || message.images.isNotEmpty() || message.files.isNotEmpty()) return
        _uiState.update { it.copy(input = message.text, editingMessageId = messageId) }
    }

    /** 取消编辑：清空输入框并退出编辑状态。 */
    fun onCancelEdit() {
        _uiState.update { it.copy(input = "", editingMessageId = null) }
    }

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
    fun onDeleteMessage(messageId: String) {
        viewModelScope.launch {
            if (messageId.toLongOrNull() == null) {
                _uiState.update { it.copy(messages = it.messages.filterNot { m -> m.id == messageId }) }
                return@launch
            }
            val ok = deleteMessage(messageId)
            if (ok) {
                _uiState.update { it.copy(messages = it.messages.filterNot { m -> m.id == messageId }) }
            } else {
                _uiState.update { it.copy(error = "删除失败，请稍后重试") }
            }
        }
    }

    /**
     * 清空当前对话的消息，保留会话本身。服务端还没有「清空消息」接口，所以先分页拉出全部消息 id，
     * 再逐条删除（每条走服务端 + 本地缓存）。任一条失败会保留它并提示数量。
     */
    fun onClearConversation() {
        viewModelScope.launch {
            val sessionId = repository.currentSessionId()
            val ids = mutableListOf<String>()
            try {
                // 按最旧的 id 一直往前翻，直到一页都没有新消息为止（不依赖 hasMore，服务端可能不可靠）
                var beforeId: Long? = null
                val seen = HashSet<String>()
                while (true) {
                    val page = withContext(Dispatchers.IO) { repository.loadHistoryPage(sessionId, beforeId, 50) }
                    val fresh = page.messages.filter { seen.add(it.id) }
                    if (fresh.isEmpty()) break
                    ids += fresh.map { it.id }
                    val oldest = fresh.mapNotNull { it.id.toLongOrNull() }.minOrNull() ?: break
                    beforeId = oldest
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "读取消息失败，请稍后重试") }
                return@launch
            }
            val removed = mutableListOf<String>()
            var failed = 0
            for (id in ids) {
                if (id.toLongOrNull() == null) {
                    removed += id
                } else if (deleteMessage(id)) {
                    removed += id
                } else {
                    failed++
                }
            }
            // 服务端全部删除成功 = 会话已经是空的：本地列表直接清空，不依赖 id 能否对上
            if (failed == 0) {
                // 本地数据库里还留着助手回复（服务端历史只返回了用户消息），这里一并清掉，否则刷新时会被读回来
                repository.clearLocalMessages(sessionId)
                _uiState.update { it.copy(messages = emptyList(), hasMoreHistory = false) }
            } else {
                _uiState.update { it.copy(messages = it.messages.filterNot { m -> m.id in removed }) }
                _uiState.update { it.copy(error = "有 $failed 条消息清空失败，请稍后重试") }
            }
        }
    }

    /**
     * 批量删除（多选）：逐条调用单条删除（服务端 + 本地缓存），成功的从列表移除；
     * 没有服务端 id 的本地消息只从列表移除。失败的保留，并提示数量。
     */
    fun onDeleteMessages(messageIds: List<String>) {
        viewModelScope.launch {
            // 只有服务端确认删除成功，才从列表移除（之前是先删本地，刷新后消息又回来）
            val removed = mutableListOf<String>()
            var failed = 0
            var unsynced = 0
            for (id in messageIds) {
                // 本地临时 id（如 local-user-*）还没有服务端 id：删不了服务端，也不能只删本地，留在列表里
                if (id.toLongOrNull() == null) {
                    unsynced++
                    continue
                }
                if (deleteMessage(id)) removed += id else failed++
            }
            _uiState.update { it.copy(messages = it.messages.filterNot { m -> m.id in removed }) }
            // 最后以服务端为准对齐一次：删失败的、没删掉的不会在刷新后「复活」
            fallbackReloadMessages()
            if (failed + unsynced > 0) {
                val parts = buildList {
                    if (failed > 0) add("$failed 条删除失败")
                    if (unsynced > 0) add("$unsynced 条尚未同步，请稍后再删")
                }
                _uiState.update { it.copy(error = parts.joinToString("，")) }
            }
        }
    }

    /** 加载更早的一页历史（按最早一条的服务端 id 作为 beforeId）。 */
    fun onLoadOlderMessages() {
        val state = _uiState.value
        if (!state.hasMoreHistory || state.loadingOlder || state.sending) return
        val oldestId = state.messages.mapNotNull { it.id.toLongOrNull() }.minOrNull() ?: return
        _uiState.update { it.copy(loadingOlder = true) }
        viewModelScope.launch {
            val page = runCatching { loadHistory(beforeId = oldestId) }.getOrNull()
            _uiState.update { cur ->
                if (page == null) {
                    cur.copy(loadingOlder = false, error = "加载更早的消息失败，请稍后重试")
                } else {
                    val known = page.messages.map { it.id }.toSet()
                    cur.copy(
                        messages = page.messages + cur.messages.filterNot { it.id in known },
                        hasMoreHistory = page.hasMore,
                        loadingOlder = false,
                    )
                }
            }
        }
    }

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
                val failedLocal = cur.messages.filter { it.role == ChatRole.USER && it.delivery == DeliveryStatus.FAILED }
                val merged = page.messages.map { m ->
                    val hit = m.role == ChatRole.USER && failedLocal.any { f ->
                        f.text == m.text && kotlin.math.abs(f.createdAtMillis - m.createdAtMillis) <= 120_000L
                    }
                    if (hit) m.copy(delivery = DeliveryStatus.FAILED) else m
                }
                // 本地还没拿到服务端 id 的用户消息（比如停止时还没保存到服务端）不能被历史覆盖掉，保留并保持失败/发送状态
                val serverIds = page.messages.map { it.id }.toSet()
                // 已经和服务端消息对上的本地失败消息，不再重复保留
                val matchedLocalIds = page.messages.filter { it.role == ChatRole.USER }.flatMap { m ->
                    failedLocal.filter { f -> f.text == m.text && kotlin.math.abs(f.createdAtMillis - m.createdAtMillis) <= 120_000L }.map { it.id }
                }.toSet()
                val localOnly = cur.messages.filter {
                    it.role == ChatRole.USER && it.id.startsWith("local-") && it.id !in serverIds && it.id !in matchedLocalIds
                }
                // 服务端历史和本地未同步的消息统一按发送/创建时间排序，本地消息不再追加在末尾（否则后发的消息会跑到前面）
                cur.copy(
                    messages = (merged + localOnly).sortedBy { it.createdAtMillis },
                    hasMoreHistory = page.hasMore,
                    sessionId = sessionId,
                )
            }
        }
    }

    /** 点赞切换，返回切换后是否为「已赞」。点赞会取消点踩。 */
    fun onToggleLike(messageId: String): Boolean {
        val liked = messageId !in _uiState.value.likedIds
        _uiState.update {
            it.copy(
                likedIds = if (liked) it.likedIds + messageId else it.likedIds - messageId,
                dislikedIds = it.dislikedIds - messageId,
            )
        }
        return liked
    }

    /** 点踩并提交反馈后调用。点踩会取消点赞。 */
    fun onDisliked(messageId: String) {
        _uiState.update { it.copy(dislikedIds = it.dislikedIds + messageId, likedIds = it.likedIds - messageId) }
    }

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

    private fun addDraft(kind: MessageKind, staged: StagedAttachment) {
        val draft = DraftAttachment(id = "draft-${System.nanoTime()}", kind = kind, staged = staged)
        _uiState.update { it.copy(drafts = it.drafts + draft) }
    }

    fun onRemoveDraft(id: String) {
        _uiState.update { it.copy(drafts = it.drafts.filterNot { d -> d.id == id }) }
    }

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
    fun onSpeakMessage(message: ChatMessage) {
        if (message.text.isBlank() || _uiState.value.speakingMessageId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(speakingMessageId = message.id) }
            val spokenOnDevice = runCatching { DeviceSpeech.speakChinese(message.text) }.getOrDefault(false)
            if (spokenOnDevice) {
                android.util.Log.i("ChatViewModel", "speak: played on device")
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

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

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
            // 转写显示在语音气泡下方（同一条消息），再把文字发给大模型
            _uiState.update { current ->
                current.copy(messages = current.messages.map { if (it.id == message.id) it.copy(text = text) else it })
            }
            streamReply(text, userMessageId = message.id, persistUser = true)
        }
    }
}
