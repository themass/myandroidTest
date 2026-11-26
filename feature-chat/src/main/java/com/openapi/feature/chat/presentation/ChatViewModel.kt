package com.openapi.feature.chat.presentation

import com.openapi.speech.audio.AudioPlayer
import com.openapi.speech.tts.DeviceSpeech
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openapi.feature.chat.ChatDependencies
import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.FileAttachment
import com.openapi.feature.chat.domain.model.ImageAttachment
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.HistoryPage
import com.openapi.feature.chat.domain.model.ChatStreamEvent
import com.openapi.feature.chat.domain.model.DeliveryStatus
import com.openapi.feature.chat.domain.model.MessageKind
import com.openapi.feature.chat.domain.model.OutgoingAttachment
import com.openapi.feature.chat.domain.model.VoiceAttachment
import com.openapi.feature.chat.domain.usecase.LoadHistoryUseCase
import com.openapi.feature.chat.domain.usecase.SendMessageUseCase
import com.openapi.speech.audio.VoiceRecorder
import com.openapi.feature.chat.roles.data.RolesDependencies
import com.openapi.feature.chat.roles.domain.SelectedRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    // 节拍与超时不在这里定义：真值在使用它们的 ReplyStreamer / AttachmentSender 里，
    // 这里曾经抄了一份没人用的副本（整改清单 P3-18），改抄本不生效纯误导，删。

    private val repository = ChatDependencies.require()
    // 错误文案端口：文字在 feature-chat 资源里，实现在 app（整改清单 P2-14b）
    private val strings = ChatDependencies.strings()
    private val loadHistory = LoadHistoryUseCase(repository)
    private val sendMessage = SendMessageUseCase(repository)
    private val roles = RolesDependencies.require()

    // 初始就是「加载中」：第一帧不该先画一个空会话，再跳成历史。
    private val _uiState = MutableStateFlow(ChatUiState(selectedRole = roles.selected(), loading = true))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /**
     * 乐观删除的墓碑：所有「用服务端历史重建列表」的路径（refresh 合并、兜底对齐、
     * 加载更早的一页）共享这一份，正在等确认的条目不能被历史带回来。见 [PendingDeletions]。
     */
    private val pendingDeletions = PendingDeletions()

    private val characterActions = CharacterActions(repository, roles, _uiState, viewModelScope, strings, onSessionChanged = ::refresh)
    private val composer = ComposerActions(_uiState)
    private val streamer = ReplyStreamer(
        repository, _uiState, viewModelScope, sendMessage,
        attachmentActive = { attachmentJob?.isActive == true },
        reconcile = { fallbackReloadMessages() },
        syncSessions = { syncSessionsList() },
        strings = strings,
    )
    private val attachments = AttachmentSender(
        repository, _uiState, streamer,
        // 图文消息：图片已经传完，文字和图片引用一起进同一个回合——只回一次话。
        streamWithAttachments = { text, messageId, persistUser, files, location ->
            streamReply(text, userMessageId = messageId, persistUser = persistUser, syncAfter = false,
                attachments = files, location = location)
        },
    )
    private val resend = ResendActions(
        repository, _uiState, viewModelScope, streamer,
        // 赋值前先取消旧句柄：不取消的话，上一条附件任务变成孤儿——停止按钮只 cancel
        // 最后一条，之前那条（还在上传）既没人等它也没人能停它，`attachmentActive` 也随之失真。
        startAttachments = { m -> attachmentJob?.cancel(); attachmentJob = viewModelScope.launch { deliverAttachments(m) } },
    )
    private val send = SendActions(
        repository, _uiState, viewModelScope, composer, streamer,
        submitEdit = { id, text -> submitEdit(id, text) },
        // 同 resend：先取消旧任务再起新的，attachmentJob 始终指向唯一在跑的那条。
        startAttachmentSend = { m -> attachmentJob?.cancel(); attachmentJob = viewModelScope.launch { repository.saveMessage(repository.currentSessionId(), m); deliverAttachments(m, restoreDraftsOnFailure = true) } },
        strings = strings,
    )
    private val history = HistoryActions(
        repository, _uiState, viewModelScope,
        isInFlight = { attachments.isInFlight(it) }, strings = strings,
        pendingDeletions = pendingDeletions,
    )
    private val feedback = FeedbackActions(_uiState)
    private val removal = MessageRemoval(
        repository, _uiState, viewModelScope, strings = strings,
        pending = pendingDeletions,
    )
    private val imageRetry = ImageRetryActions(repository, _uiState, viewModelScope)

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
            _uiState.update { it.copy(loading = true, error = null, loadError = null) }
            // 读失败不能静默成空页：那会让「断网」和「历史被清空」看起来一模一样。
            // 页面照常装配（会话列表、角色都还能用），但把失败原因留下来给消息区渲染。
            val loaded = runCatching { loadHistory() }
            val page = loaded.getOrElse { HistoryPage(emptyList(), hasMore = false) }
            val loadError = loaded.exceptionOrNull()?.let(::describeLoadFailure)
            val sessionId = repository.currentSessionId()
            val merged = mergePending(page.messages, sessionId)
            val sessions = repository.sessions()
            val current = sessions.firstOrNull { it.id == sessionId }
            val sessionRole = SessionRole.resolve(current, roles, sessionId)
            val sessionCharacterId = current?.characterId?.takeIf { it > 0 && sessionRole == null }
            // 发送时带的人设也要跟着会话走
            repository.setSelectedCharacterId(sessionCharacterId)
            if (sessionRole == null) roles.setSelected(null)
            _uiState.update {
                it.copy(
                    messages = merged,
                    hasMoreHistory = page.hasMore,
                    loadingOlder = false,
                    editingMessageId = null,
                    loading = false,
                    loadError = loadError,
                    sessionId = sessionId,
                    // 会话的角色以会话自己为准：每个角色一段独立历史，不能沿用“最近一次选的角色”
                    selectedRole = sessionRole?.also { roles.setSelected(it) },
                    selectedCharacterId = sessionCharacterId,
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

    private val sessionActions = SessionActions(repository, _uiState, viewModelScope, ::refresh, strings)

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

    /**
     * 「生成中发消息」开关（设计 20261126 §2 D1）。值推进 [ChatUiState.sendMode]，
     * 判定仍只在 [ChatTurnGate] 一处；持久化在 ChatRoute（prefs 是平台能力，ViewModel 不碰 Context）。
     */
    fun onSendModeChange(mode: SendMode) {
        _uiState.update { it.copy(sendMode = mode) }
    }

    /** 纯文字消息：插入用户气泡，然后流式接收回复。 */
    private fun sendText(text: String) = send.sendText(text)

    private fun streamReply(
        text: String?,
        userMessageId: String?,
        persistUser: Boolean,
        regenerate: Boolean = false,
        syncAfter: Boolean = true,
        replaceKey: String? = null,
        attachments: List<OutgoingAttachment> = emptyList(),
        location: com.openapi.feature.chat.domain.model.GeoLocation? = null,
    ) = streamer.stream(text, userMessageId, persistUser, regenerate, syncAfter, replaceKey, attachments, location)

    private fun updateByKey(key: String, transform: (ChatMessage) -> ChatMessage) = streamer.updateByKey(key, transform)

    private fun newPlaceholder(): ChatMessage = streamer.newPlaceholder()

    private fun setDelivery(messageId: String, status: DeliveryStatus) = streamer.setDelivery(messageId, status)

    /** 停止生成：取消网络请求，已经显示和已收到的内容都保留。 */
    fun onStopGenerating() {
        streamer.cancel()
        attachmentJob?.cancel()
    }

    /** A36①：从「N 条排队中」徽标撤回一条排队文字，回到输入框。 */
    fun onWithdrawQueued(index: Int) = composer.withdrawQueued(index)

    /** A40：「插话」——这条排队文字现在就走服务端（服务端裁决合并/插话/排队/新开）。 */
    fun onPromoteQueued(index: Int) = send.steerQueued(index)

    /** A40：「打断重发」——这条插到队首并取消当前回合，本轮一收尾就发它。 */
    fun onInterruptQueued(index: Int) = send.interruptQueued(index)

    /** 附件发送任务。停止按钮要能同时停掉两者，且两者互不覆盖。 */
    private var attachmentJob: kotlinx.coroutines.Job? = null

    /**
     * 发送带附件的用户消息：逐个上传附件，各自的识别结果合并成一条助手回复；
     * 有配文时再把配文走文字流（用户消息已保存过，不再重复保存）。
     * [restoreDraftsOnFailure]：从输入栏发起（而不是气泡重试）时为 true，
     * 失败后把附件和文字放回输入栏，用户能改一下再发（整改清单 P1-11）。
     */
    private suspend fun deliverAttachments(message: ChatMessage, restoreDraftsOnFailure: Boolean = false) =
        attachments.deliver(message, restoreDraftsOnFailure)

    private suspend fun mergePending(page: List<ChatMessage>, sessionId: Long): List<ChatMessage> =
        history.mergePending(page, sessionId)


    /** 重新发送一条失败的用户消息（不重新插入气泡，也不重复保存）。 */
    fun onRetry(messageId: String) = resend.retry(messageId)

    /**
     * 点击了加载失败的那张图片（整改清单 P1-10）。组件已强制重新加载现有地址；
     * 远程图在这里额外做一件事：重新签一个 URL 换掉旧的。
     */
    fun onRetryImage(messageId: String, index: Int) = imageRetry.retry(messageId, index)

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
        val sessions = repository.sessions()
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
                // 先剔掉乐观删除挂起中的条目：兜底对齐正是「删了又回来」最容易发生的地方
                // （删除请求还挂在后台，这一页历史已经到了）。
                cur.copy(
                    messages = HistoryMerge.merge(cur.messages, pendingDeletions.filter(page.messages)),
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

    fun onRemoveDraft(id: String) = composer.removeDraft(id)

    /** 取掉输入栏上的待发送位置 chip。 */
    fun onRemoveLocationDraft() = composer.removeLocationDraft()

    /**
     * 「位置」按钮取到一次粗定位后进这里（P2-13）。坐标先留在状态里，发送时才随消息发出；
     * 再点一次位置 = 换掉上一个位置。
     *
     * 参数刻意用散开的原始值而不是 `LocationFixer.Fix`：那是 internal 类型，
     * public 方法不能把它暴露到模块外。
     */
    fun onLocationFixReady(latitude: Double, longitude: Double, accuracyMeters: Int?) =
        composer.setLocationDraft(
            com.openapi.feature.chat.domain.model.GeoLocation(
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = accuracyMeters,
            ),
        )


    /**
     * 草稿托盘里长按图片（S3「再编辑」）：解析出这条草稿的暂存图片文件。
     * 返回 null = 不可编辑（草稿已不在、文件已丢、或不是图片），界面什么都不做。
     */
    fun onDraftImageForEdit(draftId: String): java.io.File? = send.draftImageForEdit(draftId)

    /**
     * 编辑完成：用编辑后的图片替换草稿里的暂存文件。失败时保留原图，不阻断发送。
     */
    fun onReplaceDraftImage(target: java.io.File, edited: android.net.Uri, context: android.content.Context) =
        send.replaceDraftImage(target, edited, context)

    /**
     * 相册多选或拍照：按选择顺序逐个放进草稿托盘。
     *
     * @param openEditor 传入时，每张图暂存后先交给编辑器（[file] 是暂存文件，[bitmap] 已解码），
     *                   编辑完成由调用方替换暂存文件；不传则直接加入草稿。
     */
    fun onImagesPicked(
        context: android.content.Context,
        uris: List<android.net.Uri>,
        openEditor: ((java.io.File, android.graphics.Bitmap) -> Unit)? = null,
    ) = send.imagesPicked(context, uris, openEditor)

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
