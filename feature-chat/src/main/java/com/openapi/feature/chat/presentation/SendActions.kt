package com.openapi.feature.chat.presentation

import com.openapi.speech.tts.DeviceSpeech
import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.DeliveryStatus
import com.openapi.feature.chat.domain.model.FileAttachment
import com.openapi.feature.chat.domain.model.ImageAttachment
import com.openapi.feature.chat.domain.model.MessageKind
import com.openapi.feature.chat.domain.model.SteerDisposition
import com.openapi.feature.chat.domain.repository.ChatRepository
import com.openapi.speech.audio.AudioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.FileOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 发送：文字和草稿附件的发送、选择附件、朗读助手回复。从 ChatViewModel 拆出来。
 * 编辑态的发送交给 [submitEdit]；带附件的消息由 [startAttachmentSend] 保存并发送。
 */
internal class SendActions(
    private val repository: ChatRepository,
    private val state: MutableStateFlow<ChatUiState>,
    private val scope: CoroutineScope,
    private val composer: ComposerActions,
    private val streamer: ReplyStreamer,
    private val submitEdit: (String, String) -> Unit,
    private val startAttachmentSend: (ChatMessage) -> Unit,
    private val strings: ChatStrings,
) {

    fun onSend() {
        val snapshot = state.value
        if (!snapshot.canSend) return
        val text = snapshot.input.trim()
        val drafts = snapshot.drafts
        // 正在编辑一条用户消息：提交的是编辑，而不是新消息（附件草稿不参与编辑）
        val editingId = snapshot.editingMessageId
        // 生成中：只有纯文字走排队（ChatUiState.queued，FIFO，回合自然结束后由
        // ReplyStreamer 取队首续发）。编辑/附件/位置维持旧的「生成中不能发」闸：
        // 编辑的目标消息可能已被这一轮改变，附件要等上传窗口，都不适合插进队列。
        // 判据来自 [ChatTurnGate]（读侧唯一入口）；text.isEmpty() 的特例留在这里：
        // 空输入能走到这里只可能是草稿撑起了 canSend。
        when (ChatTurnGate.of(snapshot).sendPath()) {
            ChatTurnGate.SendPath.BlockedTurnRunning -> return
            ChatTurnGate.SendPath.Queue -> {
                if (editingId == null && drafts.isEmpty() && snapshot.locationDraft == null && text.isNotEmpty()) {
                    state.update { it.copy(input = "") }
                    sendText(text)
                }
                return
            }
            ChatTurnGate.SendPath.Direct -> Unit
        }
        if (editingId != null && drafts.isEmpty()) {
            submitEdit(editingId, text)
            return
        }
        val location = snapshot.locationDraft
        state.update { it.copy(input = "", drafts = emptyList(), editingMessageId = null, locationDraft = null) }
        if (drafts.isEmpty() && location == null) {
            sendText(text)
            return
        }
        // 附件、位置与文字组成一条消息：界面上一个气泡，保存一条记录
        val now = System.currentTimeMillis()
        val images = drafts.filter { it.kind == MessageKind.IMAGE }
            .map { ImageAttachment(path = it.staged.file.absolutePath) }
        val files = drafts.filter { it.kind == MessageKind.FILE }
            .map { FileAttachment(path = it.staged.file.absolutePath, name = it.staged.displayName, sizeBytes = it.staged.sizeBytes) }
        val videos = drafts.filter { it.kind == MessageKind.VIDEO }
            .map {
                com.openapi.feature.chat.domain.model.VideoAttachment(
                    path = it.staged.file.absolutePath,
                    name = it.staged.displayName,
                    sizeBytes = it.staged.sizeBytes,
                    durationSeconds = it.staged.durationSeconds,
                    thumbnailPath = it.staged.thumbnailPath,
                )
            }
        val message = ChatMessage(
            id = "local-user-$now",
            role = ChatRole.USER,
            text = text,
            createdAtMillis = now,
            // 富类型只有一个能进 kind（一条消息一个气泡）；混发时服务端按字段各存各的，
            // kind 决定的是本地缓存行的类型，取「最先要看内容」的那个：位置 > 图片 > 视频 > 文件。
            kind = when {
                location != null -> MessageKind.LOCATION
                images.isNotEmpty() -> MessageKind.IMAGE
                videos.isNotEmpty() -> MessageKind.VIDEO
                else -> MessageKind.FILE
            },
            images = images,
            files = files,
            videos = videos,
            location = location,
            delivery = DeliveryStatus.SENDING,
        )
        state.update { it.copy(messages = it.messages + message) }
        startAttachmentSend(message)
    }

    fun sendText(text: String) {
        if (text.isBlank()) return
        val gate = ChatTurnGate.of(state.value)
        if (gate.textSendPath() == ChatTurnGate.SendPath.Queue) {
            // 排队模式（D1 开关的另一侧）：生成中不直发——按 Enter 顺序入队（FIFO），
            // 回合**自然结束**后由 ReplyStreamer 取队首续发；普通停止保留队列但不会自动续发。
            // A40：入队即带本机 id——「插话」要把这个 id 当 clientMessageId 提交给服务端，
            // 气泡与队列条目从此同一个 id，异步回执回来也不会错位。
            state.update { it.copy(queued = it.queued + QueuedText(id = "q-" + System.nanoTime(), text = text)) }
            return
        }
        val now = System.currentTimeMillis()
        val user = ChatMessage(
            id = "local-user-$now",
            role = ChatRole.USER,
            text = text,
            createdAtMillis = now,
            delivery = DeliveryStatus.SENDING,
        )
        state.update { it.copy(messages = it.messages + user, editingMessageId = null) }
        if (gate.sending) {
            // D1 即时发送（默认）：生成中也**直接 submit**——服务端单一裁决
            // （merged 原地重跑 / queued 排空成新回合），绝不本地排队。
            // 不走 streamer.stream：那条路带 MigrationGuard，同会话在飞回合会被 409 拦下；
            // submit 在回合进行中不 409（服务端直接给裁决），接住回合同样复用 noteCaughtTurn。
            submitNow(text, user.id)
            return
        }
        streamer.stream(text, userMessageId = user.id, persistUser = true)
    }

    /**
     * 立刻提交这条文字（气泡已在屏上，id = [bubbleId] = clientMessageId），按服务端裁决收尾：
     * queued/started 登记接住回合（A40 的 noteCaughtTurn 路径原样复用）；
     * merged/steered 会作为当前事件流的 restarted/input 事件自然到达，这里无事可做；
     * 失败（网络/旧服务端缺 disposition）把文字放回输入框、撤掉气泡——任何路径都不丢一条用户输入。
     */
    private fun submitNow(text: String, bubbleId: String) {
        scope.launch {
            val snapshot = state.value
            val sid = repository.currentSessionId()
            val result = repository.steerQueuedMessage(
                sid, text, snapshot.selectedCharacterId, snapshot.selectedRole?.id,
                clientMessageId = bubbleId,
            )
            if (!result.ok) {
                state.update { s ->
                    s.copy(
                        messages = s.messages.filterNot { it.id == bubbleId },
                        input = if (s.input.isBlank()) text else s.input + "\n" + text,
                        error = strings.steerSendFailed(),
                    )
                }
                return@launch
            }
            when (result.disposition) {
                // 排空/新开回合：本轮事件流收尾后由 ReplyStreamer 接住展示。
                // started 时回执带新回合 id（精确锁扣）；queued 时带的是当时在跑的回合
                // （要跳过它，认之后出现的第一条别的回合）。
                SteerDisposition.QUEUED -> streamer.noteCaughtTurn(sid, skipTurnId = result.turnId, bubbleId = bubbleId, userServerId = result.messageId, expectedTurnId = null)
                SteerDisposition.STARTED -> streamer.noteCaughtTurn(sid, skipTurnId = null, bubbleId = bubbleId, userServerId = result.messageId, expectedTurnId = result.turnId)
                // merged/steered：当前事件流自己会收到 restarted/input 事件，无需额外动作。
                else -> Unit
            }
        }
    }

    /**
     * A40「插话」：把第 [index] 条排队文字**立刻**提交给服务端（不等本轮结束）。
     *
     * 服务端按回合阶段裁决（见 ChatRepository.steerQueuedMessage 的契约）：
     * merged/steered 并进当前回合的事件流（界面什么都不用多做，`turn.restarted`
     * 会清掉旧半截）；queued/started 由 [ReplyStreamer.noteCaughtTurn] 登记，
     * 本轮收尾后接住服务端开的新回合。
     *
     * 提交失败（网络、服务端拒绝）：这条**放回输入框**并提示——文字绝不丢，
     * 用户可以改一下再发。气泡同步撤掉（它从未真正发出）。
     */
    fun steerQueued(index: Int) {
        val entry = state.value.queued.getOrNull(index) ?: return
        // 先原子地摘出队列并插气泡：气泡 id = 队列条目 id = clientMessageId，三合一。
        state.update { s ->
            val now = System.currentTimeMillis()
            s.copy(
                queued = s.queued.filterNot { it.id == entry.id },
                messages = s.messages + ChatMessage(
                    id = entry.id, role = ChatRole.USER, text = entry.text,
                    createdAtMillis = now, delivery = DeliveryStatus.SENDING,
                ),
            )
        }
        scope.launch {
            val snapshot = state.value
            val sid = repository.currentSessionId()
            val result = repository.steerQueuedMessage(
                sid, entry.text, snapshot.selectedCharacterId, snapshot.selectedRole?.id,
                clientMessageId = entry.id,
            )
            if (!result.ok) {
                // 放回输入框（同「撤回」的行为），撤掉那条永远不会完成的气泡。
                state.update { s ->
                    s.copy(
                        messages = s.messages.filterNot { it.id == entry.id },
                        input = if (s.input.isBlank()) entry.text else s.input + "\n" + entry.text,
                        error = strings.steerSendFailed(),
                    )
                }
                return@launch
            }
            when (result.disposition) {
                // 排空/新开回合：本轮事件流收尾后由 ReplyStreamer 接住展示。
                // started 时回执带新回合 id（精确锁扣）；queued 时带的是当时在跑的回合
                // （要跳过它，认之后出现的第一条别的回合）。
                SteerDisposition.QUEUED -> streamer.noteCaughtTurn(sid, skipTurnId = result.turnId, bubbleId = entry.id, userServerId = result.messageId, expectedTurnId = null)
                SteerDisposition.STARTED -> streamer.noteCaughtTurn(sid, skipTurnId = null, bubbleId = entry.id, userServerId = result.messageId, expectedTurnId = result.turnId)
                // merged/steered：当前事件流自己会收到 restarted/input 事件，无需额外动作。
                else -> Unit
            }
        }
    }

    /**
     * 「停止当前并优先回答最新一条」：将队列中最新输入提到队首、保留其余 FIFO，
     * 并请求取消当前回合；服务端确认取消后，事件流收尾并续发队首。
     * 若此刻回合已结束（竞态），直接发送这条最新输入。
     */
    fun interruptQueued(index: Int) {
        val snapshot = state.value
        val entry = snapshot.queued.lastOrNull() ?: return
        // UI 只对最新一条暴露此动作；如果旧界面/回调携带了其他 index，也始终执行最新优先语义。
        if (!streamer.isReplyActive()) {
            // 没有活跃回合：无需取消，摘下队头直接发（等同正常发送）。
            state.update { s -> s.copy(queued = s.queued.filterNot { it.id == entry.id }) }
            val now = System.currentTimeMillis()
            val user = ChatMessage(
                id = entry.id, role = ChatRole.USER, text = entry.text,
                createdAtMillis = now, delivery = DeliveryStatus.SENDING,
            )
            state.update { s -> s.copy(messages = s.messages + user) }
            streamer.stream(entry.text, userMessageId = entry.id, persistUser = true)
            return
        }
        state.update { s ->
            val rest = s.queued.filterNot { it.id == entry.id }
            s.copy(queued = listOf(entry) + rest)
        }
        // 服务端取消 → 当前流收到 turn.cancelled → Done → runTurn 末尾排空队首（就是这条）。
        // 失败静默（仓库实现内部记日志）：最坏情况是当前回合跑完才轮到这个队首，顺序仍正确。
        scope.launch { repository.cancelTurn(repository.currentSessionId()) }
    }

    fun attachmentPicked(
        context: android.content.Context,
        kind: MessageKind,
        uri: android.net.Uri,
        /**
         * 测试接缝（与 `CompanionViewModel.onImagesPicked` 同款）：默认 `Dispatchers.IO`
         * 是真线程，`advanceUntilIdle` 追不到它，暂存与清扫那一段就只能靠读代码确认。
         * 界面调用方一律用默认值。
         */
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    ) {
        // 生成中不接新附件（上传窗口属于下一个回合）——判据见 ChatTurnGate.allowAttachmentPick
        if (!ChatTurnGate.of(state.value).allowAttachmentPick) return
        scope.launch {
            val keep = stagedBytesStillInUse()
            val outcome = withContext(ioDispatcher) { AttachmentStager.stage(context, kind, uri, keep) }
            when (outcome) {
                is StageOutcome.Staged -> rejectIfOverLimit(kind, outcome.attachment)
                is StageOutcome.Rejected -> state.update { it.copy(error = outcome.reason) }
                StageOutcome.Failed -> state.update { it.copy(error = strings.attachmentReadFailed()) }
            }
        }
    }

    /**
     * 暂存目录里**还不能当垃圾删**的字节（复审 Important）。
     *
     * 两类必要的：
     * 1. 本地行引用的：一条 `local-` 行没到 SENT（上传一次都没成过）时，图片字节只在本机，
     *    `uploadId` 要么没有要么早过了 10 分钟寿命（ADR-011），重发只能重新上传这个文件；
     *    扫掉它 = 那条消息永久发不出去。
     * 2. 托盘里还没发的：App 一直开着、草稿压了一周没提交，此时再选一张图就会触发清扫。
     *
     * **宽出是故意的**（复审 nit 2，把话说实话）：第三路把屏幕上**所有**消息的附件路径都放进来了，
     * 包括已经 SENT 的那些 —— 按 [com.openapi.feature.chat.data.remote.LocalChatStore] 的口径
     * SENT 行的字节算孤儿（服务端有它的正式版，图没了能走签名 URL），本可以被回收。
     * 为什么不做「在屏 + 非 SENT」的精确覆盖：白名单是**一次快照**、不做引用计数，
     * 判据多一处就多一处写错的地方；而刷新过历史之后（`LocalChatStore.cacheRemote`
     * 把本地临时行改挂到服务端正式 id）带本机字节的行已经不在 `local-%` 那批里，
     * 光靠仓库那一路覆盖不到它们。这里要的是「**永不窄于**当时真的还要用的字节」，
     * 于是接受宁可宽一轮。代价有界且很小：一页历史最多几十条路径，效果只是这些字节
     * **延后一轮**回收（不在屏上以后照旧被扫）。
     */
    private suspend fun stagedBytesStillInUse(): Set<String> =
        repository.localAttachmentPathsInUse() +
            state.value.drafts.map { it.staged.file.absolutePath } +
            state.value.messages.flatMap { message ->
                message.images.map { it.path } + message.videos.map { it.path } + message.files.map { it.path }
            }

    /**
     * 进草稿托盘，超了附件额度（图片+视频合计）就说清楚（终评 I-1）。
     *
     * 走 `Rejected` 那条已有的「说得出原因」的路径，而不是静默丢掉这一个：从「文件」里选图片
     * 也到这里，那条路的选择器压根没有条数上限（它一次只给一张），到顶完全可能发生；
     * 「拍摄/视频」同理，它在 wire 上与图片共用同一条 attachments。
     */
    private fun rejectIfOverLimit(kind: MessageKind, staged: StagedAttachment) {
        if (!composer.addDraft(kind, staged)) {
            state.update {
                it.copy(error = strings.attachmentLimitReached(com.openapi.common.media.MAX_IMAGES_PER_MESSAGE_PICK))
            }
        }
    }

    fun imagesPicked(
        context: android.content.Context,
        uris: List<android.net.Uri>,
        openEditor: ((java.io.File, android.graphics.Bitmap) -> Unit)? = null,
        /** 测试接缝，同 [attachmentPicked]；界面调用方一律用默认值。 */
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    ) {
        if (uris.isEmpty() || !ChatTurnGate.of(state.value).allowAttachmentPick) return
        scope.launch {
            val keep = stagedBytesStillInUse()
            for (uri in uris) {
                val outcome = withContext(ioDispatcher) { AttachmentStager.stage(context, MessageKind.IMAGE, uri, keep) }
                when (outcome) {
                    is StageOutcome.Staged -> {
                        val staged = outcome.attachment
                        // 到顶就停在这一张上并说明原因（终评 I-1）：继续往下循环只会把
                        // 剩下几张也解码压缩一遍，白费等用户看着进度条跑完。
                        if (!composer.addDraft(MessageKind.IMAGE, staged)) {
                            state.update {
                                it.copy(error = strings.attachmentLimitReached(com.openapi.common.media.MAX_IMAGES_PER_MESSAGE_PICK))
                            }
                            return@launch
                        }
                        // 先入草稿再编辑：取消编辑时图片仍然可用（等同「不编辑直接发送」）
                        val editor = openEditor
                        if (editor != null) {
                            val bitmap = withContext(Dispatchers.IO) {
                                com.openapi.mediaview.edit.PhotoEditExporter.decode(android.net.Uri.fromFile(staged.file), context.contentResolver)
                            }
                            if (bitmap != null) editor(staged.file, bitmap)
                        }
                    }
                    is StageOutcome.Rejected -> state.update { it.copy(error = outcome.reason) }
                    StageOutcome.Failed -> state.update { it.copy(error = strings.imageReadFailed()) }
                }
            }
        }
    }

    /**
     * 草稿托盘里长按图片「再编辑」：找出这条草稿对应的暂存图片。
     *
     * 返回 null 的情况都按「不能编辑」处理，界面什么都不做（不弹错误、不闪提示）：
     * 草稿在用户长按的一瞬间已经被发出去或被移除是正常竞态，报错反而是噪音。
     * 非图片草稿同理——只有图片有编辑语义。
     */
    fun draftImageForEdit(draftId: String): java.io.File? {
        val draft = state.value.drafts.firstOrNull { it.id == draftId } ?: return null
        if (draft.kind != MessageKind.IMAGE) return null
        val file = draft.staged.file
        return if (file.exists()) file else null
    }

    /**
     * 用编辑后的图片覆盖草稿对应的暂存文件（同名写入，草稿本身无需替换）。
     * 失败时保留原图：用户至少还能发出未编辑的版本。
     */
    fun replaceDraftImage(target: java.io.File, edited: android.net.Uri, context: android.content.Context) {
        scope.launch {
            val ok: Boolean = withContext(Dispatchers.IO) {
                try {
                    val input = context.contentResolver.openInputStream(edited)
                    if (input == null) {
                        android.util.Log.w("SendActions", "replaceDraftImage: openInputStream 返回 null, uri=$edited")
                        false
                    } else {
                        input.use { src -> java.io.FileOutputStream(target).use { dst -> src.copyTo(dst) } }
                        true
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SendActions", "replaceDraftImage 写回失败: target=$target uri=$edited", e)
                    false
                }
            }
            if (!ok) state.update { it.copy(error = strings.imageEditFallback()) }
            // 编辑产生的临时文件用完即删
            withContext(Dispatchers.IO) { com.openapi.mediaview.edit.PhotoEditExporter.delete(edited) }
        }
    }

    fun speak(context: android.content.Context, message: ChatMessage) {
        if (message.text.isBlank() || state.value.speakingMessageId != null) return
        scope.launch {
            state.update { it.copy(speakingMessageId = message.id) }
            // 情绪 TTS（Bark）开启时跳过设备朗读：设备 TTS 一定成功，但它读不出
            // [laughs]。关掉开关（默认）时这一句为假，顺序和以前完全一致。
            val barkOn = com.openapi.speech.tts.BarkEmotionalTts.enabled
            if (!barkOn) {
                val spokenOnDevice = runCatching { DeviceSpeech.speakChinese(context, message.text) }.getOrDefault(false)
                if (spokenOnDevice) {
                    state.update { it.copy(speakingMessageId = null) }
                    return@launch
                }
            }
            val uri = runCatching { repository.synthesizeSpeech(message.id, message.text) }.getOrNull()
            state.update { it.copy(speakingMessageId = null) }
            if (uri == null) {
                state.update { it.copy(error = strings.speakFailed()) }
            } else {
                AudioPlayer.play(message.id, uri)
            }
        }
    }
}

