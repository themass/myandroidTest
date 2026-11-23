package com.openapi.ks.myapp.companion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openapi.ks.myapp.avatar.AvatarCommand
import com.openapi.ks.myapp.avatar.AvatarEventMapper
import com.openapi.ks.myapp.avatar.ProactiveKind
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File

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
    /**
     * `provider → transport` 工厂（生产接线 [CompanionTransports.create]，测试直接给替身）。
     *
     * 线路在**建会话时**由服务端定（grant.provider），这里按它取实现；ViewModel
     * 不认识任何具体 RTC 类，切线路/加线路都不碰这个文件。
     */
    private val transportFactory: (String) -> CompanionTransport = { FakeCompanionTransport() },
    /** 收件箱轮询间隔。主动消息不需要秒级到达，30 秒一次够用且省电。 */
    private val inboxPollMillis: Long = DEFAULT_INBOX_POLL_MILLIS,
    /** 图片上传入口。null = 这一路没接上（没有后端时也不该假装能传）。 */
    private val mediaUploader: ImageUploader? = null,
    /** 错误提示的文案端口（cleanup 项 2）：默认取 app 注入的资源实现，单测注入标记串。 */
    private val strings: CompanionStrings = CompanionDependencies.strings(),
) : ViewModel() {

    private val _state = MutableStateFlow(CompanionUiState())
    val state: StateFlow<CompanionUiState> = _state.asStateFlow()

    private var characterId: String? = null
    /** 当前会话的剧本 case；null = 普通语音会话。换 case 必须重建房间。 */
    private var activeCaseId: String? = null
    private var collectJob: Job? = null
    private var startupJob: Job? = null
    private var inboxJob: Job? = null
    private var connectionJob: Job? = null
    /** 图片协商结果的镜像订阅。换线路（[adoptTransport]）后必须重新接上新实现。 */
    private var imageSupportJob: Job? = null
    /** 正在跑的「逐张上传 → attachments → 文本」这一串。换会话时作废。 */
    private var sendJob: Job? = null

    /**
     * 当前正在用的房间实现与它对应的 provider。
     *
     * 构造时先按默认线路（livekit）装配一个，让没有后端的预览/测试照常可用；
     * 真正建会话拿到 grant 后，若服务端指定的 provider 不同，就在这里换成
     * 工厂解析出的新实现（旧的先断开）。UI 与 [mouthLevel] 等取值都跟着
     * [transport] 走，换实现不需要重订阅。
     */
    private var transportProvider: String = CompanionTransports.PROVIDER_LIVEKIT
    private var transport: CompanionTransport = transportFactory(transportProvider)

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
        images: List<String> = emptyList(),
        attachmentIds: List<String> = emptyList(),
    ) = CompanionMessage(
        role = role,
        text = text,
        streaming = streaming,
        proactive = proactive,
        confirmationId = confirmationId,
        images = images,
        attachmentIds = attachmentIds,
        id = nextMessageId++,
    )

    /** 没有 [CompanionApi] 时直接连替身，用于预览与离线调试。 */
    fun start(characterId: String, caseId: String? = null) {
        // 同一角色 + 同一 case：已经连上/正在重连/启动流程还在跑，都不能再建一个房间（会泄漏上一个 Room）。
        // 只有出错或空闲（启动流程已结束）才允许重试。换 case（含普通↔剧本）必须重建。
        if (this.characterId == characterId && this.activeCaseId == caseId) {
            val connection = _state.value.connection
            val inFlight = startupJob?.isActive == true
            if (inFlight || connection == CompanionConnection.CONNECTED || connection == CompanionConnection.RECONNECTING) return
        }
        this.characterId = characterId
        this.activeCaseId = caseId
        _state.update { it.copy(connection = CompanionConnection.CONNECTING, error = null) }
        startupJob?.cancel()
        // 新一通会话开始了：还在途的那串「上传 → attachments → 文本」属于上一个房间，
        // 让它跑完只会把一条消息发进一个已经不存在的房间里。
        //
        // `uploading` 必须在这里归零，不能指望被取消的协程自己清：它的两个清零点
        // （失败分支、deliver）都在协程**内部**，取消到不了它们。协程还没轮到跑就被取消时，
        // 它一行都没执行——`uploading` 会一直是 true，`canSend` 从此恒为 false，
        // 发送区死到路由销毁这个 ViewModel 为止。
        sendJob?.cancel()
        sendJob = null
        _state.update { it.copy(uploading = false) }
        startupJob = viewModelScope.launch {
            try {
                val session = when {
                    api == null -> offlineSession(characterId)
                    caseId != null -> api.createScriptSession(characterId, caseId)
                    else -> api.createSession(characterId)
                }
                adoptTransport(session)
                restoreHistory(session)
                // 先订阅、后连接：所有实现都成立（Fake 的事件流是冷流，先连就丢；
                // LiveKit 的 RoomEvent.Connected 在订阅之后才发；测试替身用 replay 也兼容）。
                // 以前这里按「是不是 LiveKitCompanionTransport」分三种顺序——那是把实现
                // 细节泄漏进 ViewModel，多一家实现就要再加一个分支。
                observeConnection()
                observeImageSupport()
                listen()
                transport.connect(session)
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

    /**
     * 用户选好了一张图（相册多选或拍照回来）。
     *
     * 只登记，不上传：用户可能选完就退出去，也可能连着选七八张再改主意。上传发生在
     * 点「发送」那一刻（见 [send]），失败的图留在待发条里等他重试。
     *
     * 去重按绝对路径：同一条路径重复进来是选择器/暂存层重入，不是用户又选了一张。
     */
    fun addPendingImage(file: File) {
        _state.update { st ->
            if (st.pendingImages.any { it.path == file.absolutePath }) return@update st
            if (st.pendingImages.size >= MAX_PENDING_IMAGES) {
                // 选择器一次最多给 9 张，走到这里说明是**第二次**打开选择器。这张必须
                // 说得出为什么没进去：悄悄吞掉一张图，用户只会觉得「我明明选了」。
                return@update st.copy(error = strings.imageLimitReached(MAX_PENDING_IMAGES))
            }
            st.copy(pendingImages = st.pendingImages + PendingImage(file, file.absolutePath))
        }
    }

    /**
     * 用户从待发条里划掉一张。
     *
     * 顺手删掉暂存文件：这张图没有任何消息引用它了，留着就是永久占盘的垃圾。
     * 发送成功那条路**不**删——乐观气泡的 `images` 用的正是这些本地路径，删了
     * 用户刚发出去的那条消息立刻变成一张坏图。
     */
    fun removePendingImage(path: String) {
        // VM 侧的 `uploading` 守卫（cleanup 项 4）：界面那道门（`PendingImageStrip(enabled = false)`）
        // 只是某一次重组的结果，不是合同；真正的危险在这条路的副作用——上传协程正在**按路径**
        // 逐张读文件，这里删掉文件，它下一次读到的就是一个不存在的文件，用户得到的是一句
        // 「部分图片上传失败」，原因却是他自己刚刚划掉的那一张。上传在途就什么都不做。
        if (_state.value.uploading) return
        _state.update { st -> st.copy(pendingImages = st.pendingImages.filterNot { it.path == path }) }
        runCatching { File(path).delete() }
    }

    /**
     * 相册多选或拍照回来的 uri：读出来、摆正、压成待发文件，进待发条。
     *
     * 为什么现在就落盘而不是等发送时：选择器给的 `content://` uri 的读权限随选择器会话
     * 失效，用户选完图去回个消息再回来点发送，原图就打不开了。解码+压缩放 IO 线程，
     * 一次选九张原图在手机上是几百毫秒到几秒的主线程卡顿。
     *
     * 单张读不出来就跳过那一张（其余照常进待发条）：这里还没有「发送」这个动作，
     * 为一张坏图把用户选的其他图一起丢掉反而更糟。
     */
    /**
     * [ioDispatcher] 是测试接缝（cleanup 项 6）：默认 `Dispatchers.IO`，单测传测试调度器
     * 才能把「落盘 + 清扫」这一串钉成确定性的断言（真实 IO 线程在 `runCurrent` 下追不到）。
     * 界面调用方一律用默认值。
     */
    fun onImagesPicked(
        context: android.content.Context,
        uris: List<android.net.Uri>,
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
    ) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            // 同一份 :common 的暂存清扫（cleanup 项 5）：陪伴页这里以前只写不删，
            // 「选完就退出」「第二次选图被额度挡下」都会留下没人引用的字节。
            val dir = java.io.File(context.filesDir, ATTACHMENT_DIR).apply { mkdirs() }
            // 待发条里那张还没发出去的图不是孤儿（复审 Important 的同一条规矩）：
            // 托盘活在进程里，App 开着不动放够 7 天，再选一次图就会把待发条那张删掉——
            // 卡片还显示着，点发送却得到「部分图片上传失败」，而且怎么按都失败。
            // 与对话页不同，这里没有本地行可查，白名单就是当前 state 里的那几条路径。
            val keep = _state.value.pendingImages.map { it.path }.toSet()
            withContext(ioDispatcher) {
                com.openapi.common.media.StagingGc.sweep(dir, keep = keep)
                for (uri in uris) {
                    val stamp = nextStagingStamp()
                    com.openapi.common.media.MediaStager.stageImage(context, uri, dir, stamp)
                        ?.let { addPendingImage(it) }
                }
            }
        }
    }

    /**
     * 暂存文件名用的时间戳。
     *
     * 必须是**单调递增**的：一次多选里九张图落在同一毫秒很常见，都用
     * `currentTimeMillis()` 的话后一张会盖掉前一张，用户看到的是「选了九张只剩一张」。
     *
     * 而且必须是**原子**的（复审 minor 1）：取 stamp 的位置在 `withContext(ioDispatcher)` 里面，
     * 「相册多选」和「拍照」两条路可以同时跑，两个协程落在 IO 的不同线程上。
     * 一句普通的读-改-写会在同一毫秒里读到同一个旧值，于是两条路写出同一个
     * `img_<stamp>.jpg`，后一张**静默覆盖**前一张（写文件按名字覆盖，一声不吭）。
     * 所以这里用 CAS 而不是 `lastStagingStamp = …`：同一个旧值只有一个线程换得动，
     * 换不动就重读重试。
     */
    private var lastStagingStamp = 0L
    private fun nextStagingStamp(): Long {
        val now = System.currentTimeMillis()
        lastStagingStamp = if (now > lastStagingStamp) now else lastStagingStamp + 1
        return lastStagingStamp
    }

    /**
     * 发送。
     *
     * 带图的那条路是**一次全有或全无**：逐张上传，全部成功才把 attachments 和文本发出去；
     * 任何一张失败就整条中止、图留在待发条里等用户再按一次。半发（文本进去了、图没有）
     * 的形态是「她回了一句『图我看到了』而界面上根本没有图」，比整条没发出去更难解释。
     *
     * 出站顺序是契约的一部分：`sendAttachments` 先、`sendText` 后，服务端才能把这两条
     * 挂成一条消息，然后让文本触发她的回合。
     */
    fun send() {
        val current = _state.value
        if (!current.canSend) return
        val text = current.input.trim()
        val pending = current.pendingImages
        if (pending.isEmpty()) {
            // 纯文本保持**同步**发出去，不进协程：它没有任何要等的东西，而一进协程，
            // 连点两次就会在第一个协程跑起来之前把同一句话发出去两遍。
            // 带图的那条路才需要协程（上传与 `await` 发布都是挂起点），见下面。
            sendTextNow(text, emptyList(), emptyList())
            return
        }
        val uploader = mediaUploader
        // 判的是**快照**而不是再读一次 `_state.value`（cleanup 项 7）：这条消息的内容在点发送
        // 那一刻已经定了，中途 `imageSupported` 被协商改写不会让已经走到这里的发送换口径——
        // 要么按快照放行，要么按快照停下，不会读到「一半新一半旧」。
        if (uploader == null || !current.imageSupported) {
            // 只有「+」在没协商成时不出现，正常走不到这里；走到就说明服务端的口径在
            // 这条会话里变了（重连换了一个不支持图片的 agent）。宁可停下来说清楚，
            // 也不要把文本单独发出去——那正是上面说的半发。
            _state.update { it.copy(error = strings.uploadUnsupported()) }
            return
        }
        // 上传期间把发送键按住：canSend 里的 uploading 是唯一的重入闸，
        // 一条消息发两遍会让服务端收到两组附件、她的回合被触发两次。
        _state.update { it.copy(uploading = true, error = null) }
        // 循环走的是**点发送那一刻**的快照（[pending]）：这条消息的内容在那一刻就定了，
        // 输入区在上传期间也不接受改图（见 CompanionComposer 的 uploading）。
        sendJob = viewModelScope.launch {
            val ids = ArrayList<String>(pending.size)
            var failed = false
            for (image in pending) {
                // `try/catch` 而不是 `runCatching`：后者会把 CancellationException 也吞成
                // 「这张传失败了」，于是换角色取消掉这条协程时，用户看到的是一句
                // 「部分图片上传失败」——他没点失败，也没得可重试。取消必须原样往外抛。
                val uploaded = try {
                    uploader.upload(image.file, MIME_JPEG)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logUploadFailure(image, e)
                    null
                }
                if (uploaded == null) {
                    failed = true
                    break
                }
                ids += uploaded
            }
            if (failed) {
                _state.update { it.copy(uploading = false, error = strings.uploadPartialFailed()) }
                return@launch
            }
            deliver(text, pending.map { it.path }, ids)
        }
    }

    /**
     * 带图的出站：先**等 attachments 真正发布完成**，再发文本。
     *
     * 等的是「已落进房间」这个事实，不是「我们调用过」：服务端的 pending 槽只活到
     * 下一次用户回合，文本抢跑一次，图片就落到下一条消息上去
     * （见 [CompanionTransport.sendAttachments]）。
     *
     * **发布失败就整条中止**（终评 I-3）：`sendAttachments` 现在会把「没落进房间」如实回出来，
     * 这里必须照着上传失败那条路走——不发文本、输入框与待发条原样留着、把原因说给用户。
     * 以前这里等的是「调用过」，五条失败路径全都静默返回，于是网络一抖就是「文本发出去了、
     * 图既没发出去也没留下」，正好是 [send] 那条 KDoc 声明要避免的半条消息。
     * 图**不**在这里重传：字节还在待发条里，用户再按一次发送就是完整的一条消息。
     */
    private suspend fun deliver(text: String, localPaths: List<String>, uploadIds: List<String>) {
        attachmentSeq += 1
        val delivered = transport.sendAttachments(uploadIds, "att-${System.currentTimeMillis()}-$attachmentSeq")
        if (!delivered) {
            _state.update { it.copy(uploading = false, error = strings.attachmentDeliveryFailed()) }
            return
        }
        sendTextNow(text, localPaths, uploadIds)
    }

    /**
     * 文本进房间，并落下乐观气泡。
     *
     * **同步**：纯文本那条路不该为了「统一」而进协程——一进协程，连点两次就会在第一个
     * 协程跑起来之前把同一句话发出去两遍（带图那条有 `uploading` 当闸，纯文本没有）。
     */
    private fun sendTextNow(text: String, localPaths: List<String>, uploadIds: List<String>) {
        transport.sendText(text)
        _state.update {
            it.copy(
                input = "",
                uploading = false,
                pendingImages = emptyList(),
                messages = it.messages +
                    newMessage(MessageRole.USER, text, images = localPaths, attachmentIds = uploadIds) +
                    // 先占位，收到 TextDelta 往里追加；TurnDone 时关掉 streaming。
                    newMessage(MessageRole.ASSISTANT, "", streaming = true),
            )
        }
    }

    /**
     * 上传失败记一行原因（哪一张、什么异常），供「为什么传不上去」有处可查。
     *
     * [runCatching] 不是随手加的护栏：`android.util.Log` 在普通 JVM 单测里是未 mock
     * 的桩，调用就抛。诊断日志如果能把用户看得见的错误提示一起带下水，那它就不是
     * 诊断日志——这条路径上「用户知道图还在、能重试」比这一行日志重要。
     */
    private fun logUploadFailure(image: PendingImage, error: Throwable) {
        runCatching {
            android.util.Log.w(TAG, "图片上传失败 path=${image.path} type=${error.javaClass.simpleName}")
        }
    }

    /**
     * 长按动作轮盘的发送：把动作文案（「摸了摸她的头」）当作用户的话直接发出去。
     *
     * 与 [send] 同一条出站链路（sendText + 用户气泡 + 流式占位），区别只有两点：
     * 文案不经输入框（输入框可能是空的，也不该被动作文案覆盖），闸口不看输入框
     * 有无内容。她的回话沿同一事件流回来，字幕照常滚动。
     *
     * **但它不能绕过 [CompanionUiState.uploading]**：那条路是 `send()` 的闸，不是输入框的闸。
     * 上传在途时从这里发一条纯文本出去，正是本任务要防的半条消息形状——attachments 还在
     * 路上，而服务端的 pending 槽只活到下一次用户回合，图会挂到这条动作文案上去。
     * 不走 [deliver] 是因为那条会清空输入框与待发条，而动作文案既不该覆盖草稿、
     * 也不该把用户选好的图一起发掉。
     */
    fun sendActionText(text: String) {
        if (_state.value.connection != CompanionConnection.CONNECTED) return
        if (uploadInProgress()) return
        transport.sendText(text)
        _state.update {
            it.copy(
                messages = it.messages +
                    newMessage(MessageRole.USER, text) +
                    newMessage(MessageRole.ASSISTANT, "", streaming = true),
            )
        }
    }

    /**
     * 上传闸：`send()` 的 `uploading` 那一段时间里，任何**另起一条文本回合**的入口都从这里过
     * （[sendActionText]、[regenerateLastReply]；[send] 自己走 `canSend`，那条已经含同一判据）。
     *
     * 返回 true 表示「这里必须停下」，并顺手把原因写给用户——不说清楚就是「点了没反应」，
     * 而用户看不到输入区为什么按不动。闸只有一处实现：再加一个出站入口时忘了它，
     * 就会重新长出「attachments 还在路上、文本先开回合」那个形状（终评 I-2 漏掉的正是这里）。
     */
    private fun uploadInProgress(): Boolean {
        if (!_state.value.uploading) return false
        _state.update { it.copy(error = strings.uploadInProgress()) }
        return true
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
     *
     * **它和 [send] / [sendActionText] 一样不能绕过上传闸**（终评 I-2）：它发出去的是一条
     * 全新的文本回合，而 attachments 事件可能还在路上——服务端的 pending 槽只活到最后那次
     * 用户回合，文本抢跑一次，图就落到下一条消息上去。这一条以前只查连接和 streaming，
     * 漏了 uploading，正是 [sendActionText] 那条 KDoc 写明的同一个形状。
     */
    fun regenerateLastReply() {
        val current = _state.value
        if (current.connection != CompanionConnection.CONNECTED) return
        if (uploadInProgress()) return
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
                        // cleanup 项 3：连接状态变化**不**清 error。
                        //
                        // 以前 CONNECTED / RECONNECTING / CONNECTING 三条边都把 error 抹成 null，
                        // 于是「部分图片上传失败，已保留待重发」会被一次普通的断线重连（LiveKit
                        // 的常态）带走：图还在待发条里，线索却没了，用户以为已经发出去了。
                        // 错误条只有两个正当的消失时机：用户自己关掉（[clearError]），
                        // 或开了一通新会话（[start] 开头就把 error 清掉）。
                        // 连接本身的状态由 [CompanionUiState.connection] 单独表达，不需要借 error 说话。
                        error = current.error,
                    )
                }
            }
        }
    }

    /**
     * 图片协商结果（`media.image`）镜像进界面。
     *
     * 为什么在 ViewModel 里镜像一份而不是让界面直接读 transport：界面拿到的是
     * [CompanionViewModel]，不该为了一个按钮去认识传输层；而且换线路时
     * [adoptTransport] 会换掉实现，界面那一侧没有重新订阅的机会，会一直看着旧房间的答案。
     *
     * 协商比连接晚（hello 发出去、ack 晚几百毫秒到），所以它是会变的：谈成之前「+」
     * 就是不出现，不要先给一个按下去什么都不会发生的入口。
     */
    private fun observeImageSupport() {
        imageSupportJob?.cancel()
        imageSupportJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            transport.imageSupported.collect { supported ->
                _state.update { it.copy(imageSupported = supported) }
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
        // 协议解析只有一处（AvatarEventMapper）。这里曾自带一份 state/emotion 的
        // 正则，与 mapper 各解各的，改协议时必漏一边——现在只翻译指令 → UI state 字段。
        when (val command = AvatarEventMapper.mapJson(raw)) {
            is AvatarCommand.SetState -> _state.update { it.copy(avatarState = command.state.name.lowercase()) }
            is AvatarCommand.Emotion -> _state.update { it.copy(emotion = command.name) }
            // 非表现类指令（嘴型等）与未识别事件都不进 UI state。
            else -> Unit
        }
        // gesture/gaze 还没有对应的 AvatarCommand（mapper 把它们归入 else），
        // 正则暂时留在这里；接入 mapper 时连同 UI state 字段一起搬。
        val gesture = Regex("\"type\"\\s*:\\s*\"gesture\".*?\"id\"\\s*:\\s*\"([^\"]+)\"")
            .find(raw)?.groupValues?.get(1)
        val gazeX = numberField(raw, "x").takeIf { "\"type\"\\s*:\\s*\"gaze\"".toRegex().containsMatchIn(raw) }
        val gazeY = numberField(raw, "y").takeIf { gazeX != null }
        if (gesture != null || (gazeX != null && gazeY != null)) {
            _state.update {
                it.copy(
                    gesture = gesture ?: it.gesture,
                    gestureSeq = if (gesture != null) it.gestureSeq + 1 else it.gestureSeq,
                    gazeX = gazeX ?: it.gazeX,
                    gazeY = gazeY ?: it.gazeY,
                    gazeSeq = if (gazeX != null && gazeY != null) it.gazeSeq + 1 else it.gazeSeq,
                )
            }
        }
    }

    private fun numberField(raw: String, name: String): Float? {
        val text = Regex("\"$name\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)")
            .find(raw)?.groupValues?.get(1) ?: return null
        return text.toFloatOrNull()
    }

    /**
     * 她说话的瞬时振幅 0..1（来自远端音频轨的 PCM）。
     *
     * 直接从 transport 转发，不进 [CompanionUiState]：这是每秒几十次的高频采样，
     * 塞进 UI state 会让整棵界面树按音频帧重组。形象层单独 collect 它。
     */
    val mouthLevel: StateFlow<Float> get() = transport.mouthLevel

    /** 她的声音的真 PCM（16k 单声道），供 Duix / Tao 口型使用。 */
    val remotePcm: kotlinx.coroutines.flow.Flow<ByteArray> get() = transport.remotePcm

    /**
     * dh-lab 测试语音 rig 的注入口：把这帧 16k mono PCM16LE 从**真远端音频轨回调的
     * 同一个下游入口**送进形象链（电平提取 → mouthLevel → bridge → setMouth）。
     * transport 是私有字段，rig 只能从这里进——这也保证 rig 摸不到 send/connect 等
     * 房间能力。返回 false = 当前替身没有音频通路，调用方降级到旧的 bridge 直喂路径。
     */
    fun injectLabAudioPcm16k(batch: ByteArray): Boolean = transport.injectLabAudioPcm16k(batch)

    /** 用户自己的音量 0..1（本地麦克风采集轨）。驱动通话页底部波形。 */
    val selfLevel: StateFlow<Float> get() = transport.selfLevel

    /** 你的声音的频段能量（低频在前），与 [selfLevel] 一起驱动底部波形的形状。 */
    val selfBands: StateFlow<FloatArray> get() = transport.selfBands

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
        imageSupportJob?.cancel()
        imageSupportJob = null
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
                        attachmentIds = item.attachmentIds,
                    )
                },
            )
        }
        // 图片是第二步，而且**不挡建房**：换签名 URL 一张要 0.4–2 秒，把它放在这条
        // 启动链上等于让语音房间晚两秒才连上。文字与灰占位先画出来，URL 到了再补图。
        hydrateImages()
    }

    /**
     * 把历史里的图片附件 id 换成可渲染的签名 URL，逐位填回 [CompanionMessage.images]。
     *
     * 三条刻意的语义（与「对话」页的 `hydrateRemoteImages` 同一套）：
     * - **并发**：一张一次 `GET /uploads/{id}`，串行做时一页五张图就是 2–10 秒才出图；
     *   `coroutineScope` 里一起发出去，整页只等最慢的一张。
     * - **去重**：同一个 id 在一页里出现多次只换一次。
     * - **单张失败不拖垮整页**：换不到就填 [CompanionMessage.images] 的哨兵空串（灰占位），
     *   既不报错也不清掉这条消息的文字。
     *
     * 只处理 `images` 还为空的消息：乐观消息（Task 4）的 images 是本地路径，不能被远程 URL 盖掉。
     * 签名 URL 不进日志、不落缓存——它只活几百秒，缓存等于给用户留一张坏图。
     */
    private fun hydrateImages() {
        val remote = api ?: return
        val targets = _state.value.messages
            .filter { it.attachmentIds.isNotEmpty() && it.images.isEmpty() }
            .associate { it.id to it.attachmentIds }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            val urls = coroutineScope {
                targets.values.flatten().distinct()
                    .associateWith { id -> async { runCatching { remote.attachmentUrl(id) }.getOrNull() } }
                    .mapValues { (_, deferred) -> deferred.await() }
            }
            _state.update { st ->
                st.copy(messages = st.messages.map { m ->
                    val ids = targets[m.id]
                    if (ids == null || m.images.isNotEmpty()) m
                    else m.copy(images = ids.map { urls[it] ?: "" })
                })
            }
        }
    }

    /**
     * 灰占位上点一下：给那一张图重新换一次签名 URL（终评 I-4）。
     *
     * 为什么必须有：签名 URL 只活 300 秒，而 [hydrateImages] 一辈子只在 [restoreHistory] 里跑一次。
     * 于是任何一条「打开页面时恰好没换到 URL」的历史图片，五分钟后就永远是灰块——
     * 而 [CompanionMessage.attachmentIds] 恰恰是为了这一刻才特意留在消息上的。
     * chatkit 的失败占位本来就画了重试图标并发出 [com.openapi.chatkit.model.MessageAction.RetryImage]，
     * 「对话」页接了它（`ImageRetryActions`），陪伴页以前把它咽进了 `else -> Unit`。
     *
     * 只重取 [index] 那一张，不整条重来：同一条消息里已经换到的那些 URL 是有效凭证，
     * 一起重取等于允许一次失败把一张好图换成灰的（与 [com.openapi.chatkit.model.MessagePart.Images]
     * 的下标语义一致——组件传回来的就是用户点的那一格）。
     *
     * 换不到就什么都不改：灰占位还在，用户可以再点一次。这里不报错——读图失败不是连接失败。
     */
    fun retryImage(messageId: Long, index: Int) {
        val remote = api ?: return
        val ids = _state.value.messages.firstOrNull { it.id == messageId }?.attachmentIds ?: return
        val uploadId = ids.getOrNull(index) ?: return
        viewModelScope.launch {
            val url = runCatching { remote.attachmentUrl(uploadId) }.getOrNull()
            if (url.isNullOrEmpty()) return@launch
            _state.update { st ->
                st.copy(messages = st.messages.map { m ->
                    if (m.id != messageId) m
                    else m.copy(images = m.images.mapIndexed { i, path -> if (i == index) url else path })
                })
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

                    is CompanionEvent.ClearAvatarEmotion -> _state.update {
                        // 降级进 L2（B3）：表情到 UI state 这一层收回 null，
                        // 界面那一跳的 LaunchedEffect 会把它翻成 ClearEmotion 指令。
                        it.copy(emotion = null)
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

    /**
     * 按服务端在 grant 里指定的 provider 换房间实现。
     *
     * provider 没变就什么都不做（每次通话都会走到这里，不能白白重连）。变了说明
     * 服务端把这条会话切到了另一条线路（容灾/国内流量切换）：先断开旧的，再从工厂
     * 取新的。此时还没 connect 新会话，所以换实现是安全的。
     */
    private suspend fun adoptTransport(session: SessionResponse) {
        val provider = session.provider.trim().lowercase().takeIf { it.isNotEmpty() }
            ?: CompanionTransports.PROVIDER_LIVEKIT
        if (provider == transportProvider) return
        runCatching { transport.disconnect() }
        transportProvider = provider
        transport = transportFactory(provider)
    }

    private fun appendToStreaming(delta: String) {
        _state.update { st ->
            val idx = st.messages.indexOfLast { it.streaming }
            if (idx < 0) {
                // 语音通话里没人先发 send()，没有占位可追加。以前直接丢弃 →
                // 字幕永远空，「字幕开关」看着没效果。第一个 delta 自己开一条流式回复。
                return@update st.copy(messages = st.messages + newMessage(MessageRole.ASSISTANT, delta, streaming = true))
            }
            st.copy(messages = st.messages.toMutableList().also {
                it[idx] = it[idx].copy(text = it[idx].text + delta)
            })
        }
    }

    /**
     * attachments 的 `client_msg_id` 里的自增位。`att-<epochMillis>-<seq>`：
     * 毫秒会撞（连点两次发送、或同一毫秒内两条都发出去），seq 保证服务端拿到
     * 的是两个不同的幂等键——不然第二组附件会被当作第一组的重发直接丢掉。
     */
    private var attachmentSeq = 0L

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
        imageSupportJob?.cancel()
        // 上传跑到一半页面没了：这一串要作废，否则协程在 viewModelScope 里也迟早会
        // 走到 deliver()，把一条用户已经看不见的消息发进房间。
        sendJob?.cancel()
        sendJob = null
        viewModelScope.launch { transport.disconnect() }
    }

    companion object {
        const val DEFAULT_INBOX_POLL_MILLIS = 30_000L

        /**
         * 一条消息最多带几张图。真值在 :common 的
         * [com.openapi.common.media.MAX_MESSAGE_IMAGES]——那里同时钉着服务端的
         * `MAX_MESSAGE_ATTACHMENTS` 和「对话」页的同一道闸；这里只是本页的旧名字，
         * 留着一个别名比再抄一个 9 安全。
         */
        const val MAX_PENDING_IMAGES = com.openapi.common.media.MAX_MESSAGE_IMAGES

        /** 选中的图暂存在 `filesDir` 下的这个目录（FileProvider 已授权 files 根目录）。 */
        const val ATTACHMENT_DIR = "companion_attachments"

        /**
         * 暂存文件一律是 JPEG：[com.openapi.common.media.MediaStager] 统一按 EXIF 摆正后
         * 重编码成 `img_*.jpg`，拍照那条路（相机直接写的也是 jpg）同理。写死而不是按扩展名
         * 猜：猜错一个 mime 服务端会直接 415，而这里根本没有能猜错的东西。
         */
        private const val MIME_JPEG = "image/jpeg"

        private const val TAG = "CompanionViewModel"
    }

    /**
     * 图片上传的入口。测试给一个假实现就能钉住「第几张会失败」，不必起 HTTP。
     *
     * 形状与 `:common` 的 `MediaUploader.upload` 一致（失败抛异常）：调用方要区分
     * 「这张传失败了」和「整条别发」，把它塞进返回值里会让成功路径也要判一次 null。
     *
     * `fun interface` 是为了接线处能直接给一个 lambda——生产接线就是
     * `MediaUploader::upload` 那一层，不值得为它写一个类。
     */
    fun interface ImageUploader {
        /** @throws Exception 服务端拒绝、断网、未登录 */
        suspend fun upload(file: File, mime: String): String
    }
}
