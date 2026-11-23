package com.openapi.ks.myapp.companion

import com.openapi.ks.myapp.avatar.ProactiveKind
import java.io.File

/**
 * 她**怎么回**。这是"输出形式"，不是"能不能语音"。
 *
 * 以前这里是 `CompanionMode { TEXT, VOICE }`，名字看着像输出形式，实际语义却是
 * "进不进语音房间"——用户界面上那个「文字 / 语音」分段按钮因此毫无意义：
 * 点「语音」并不会让她回语音条，只是换了条传输路径。用户的原话是「一点意义都没有」。
 */
enum class CompanionOutput { TEXT, VOICE }

enum class CompanionConnection {
    IDLE,
    CONNECTING,
    CONNECTED,

    /**
     * 掉线重连中。单独一档，而不是并进 [ERROR]：重连时对话现场仍然是活的，
     * 界面该做的是「告诉她正在重连」，而不是把她当作错误状态清掉。
     */
    RECONNECTING,
    ERROR,
}

enum class MessageRole { USER, ASSISTANT }

/**
 * 选好但**还没发出去**的一张图。
 *
 * 落盘成文件而不是抱着 `content://` uri：选择器给的 uri 读权限随会话失效，
 * 用户选完去回了个微信再回来点发送，拿到的就是一张读不出来的图。
 * [path] 是 [file] 的绝对路径，单独留一份给渲染层（coil 直接吃字符串），
 * 免得 UI 层去碰 `File`。
 */
data class PendingImage(val file: File, val path: String)

data class CompanionMessage(
    val role: MessageRole,
    val text: String,
    /** 正在流式接收：界面显示打字光标，不显示操作按钮。 */
    val streaming: Boolean = false,
    /**
     * 这条消息是不是她主动发起的。主动消息的呈现要与回复区分开——用户需要一眼
     * 看出「没人跟她说话，是她自己开口的」，否则会觉得系统在替他说话。
     */
    val proactive: ProactiveKind = ProactiveKind.REPLY,
    /** 待确认的动作 id。非空时界面显示确认/取消，而不是当作普通消息。 */
    val confirmationId: String? = null,
    /**
     * 这条回复的语音时长（秒）。非空表示有语音条可以播放。
     * 现在还没有数据来源——服务端把 TTS 音轨推给房间，客户端拿到的是连续音频流，
     * 没有"这句话多长"的边界；要真正支持语音条，需要服务端在事件里带上分句与时长。
     */
    val voiceSeconds: Int? = null,
    /**
     * 稳定标识，由 [CompanionViewModel] 生成。
     * 列表里的位置会随删除而变，用下标当 id 会让「删除第 3 条」在下一条消息到达后删错人。
     */
    val id: Long = 0L,
    /**
     * 这条消息的图片**渲染源**：本地图路径（乐观消息，刚选好还没回来）或签名 URL（历史）。
     *
     * 空串是一个**哨兵**，表示「这张图换不到地址」（404 / 登录失效 / 响应坏了）：位置要留着，
     * 用户才知道这里本来有一张图、点它去重试；少一格会变成「图片凭空少了一张」。渲染层
     * 由 chatkit 的 `surfaceVariant` 底画成灰占位，不需要陪伴页自己的组件。
     */
    val images: List<String> = emptyList(),
    /**
     * 服务端给的图片附件 id（`upload_id`），与 [images] 逐位对应；本条消息自己发的图也有。
     *
     * 留着它才有「重试换源」的可能——签名 URL 只活几百秒，过期后唯一能重新换取的凭据就是这个 id。
     */
    val attachmentIds: List<String> = emptyList(),
)

data class CompanionUiState(
    /** 她怎么回：文字 or 语音（语音时同时出语音条 + 文字）。 */
    val output: CompanionOutput = CompanionOutput.TEXT,
    /**
     * 是否在全屏通话形态。由用户点「通话」进入，不是常驻状态。
     * 它和 [output] 是两个维度：通话页里也可能只是听声音，不开语音输出。
     */
    val callActive: Boolean = false,
    val connection: CompanionConnection = CompanionConnection.IDLE,
    /**
     * 本地麦克风是否关闭。只描述「我这边关没关麦」，与 [connection] 无关：
     * 没给录音权限进来时也是静音，但房间照样连着，字幕照常收。
     */
    val isMuted: Boolean = false,
    val messages: List<CompanionMessage> = emptyList(),
    val avatarState: String = "idle",
    val emotion: String? = null,
    /** 最近一次手势 id；用自增 [gestureSeq] 触发一次性 dispatch。 */
    val gesture: String? = null,
    val gestureSeq: Long = 0L,
    /** 最近一次注视；[gazeSeq] 同样保证连发同方向也能各派一次。 */
    val gazeX: Float? = null,
    val gazeY: Float? = null,
    val gazeSeq: Long = 0L,
    val input: String = "",
    val error: String? = null,
    /** 有主动来电时非空：界面切到来电页，而不是插一条消息。 */
    val incomingCall: ProactiveKind? = null,
    /** 形象区是否展开。默认收起——那个占屏 42% 的大框用户根本不知道是什么。 */
    val avatarExpanded: Boolean = false,
    /** 选好还没发出去的图；发送成功后清空，上传失败时原样留着待重发。 */
    val pendingImages: List<PendingImage> = emptyList(),
    /** 「+」入口是否可用：由 hello_ack 是否谈成 `media.image` 决定。 */
    val imageSupported: Boolean = false,
    /** 图片正在上传：这一段时间输入区的发送键要按住。 */
    val uploading: Boolean = false,
) {
    /**
     * 能不能发。**只有图不算能发**（裁定：companion 的回合由文本触发，纯图消息 P1 不做，
     * 见设计 §5 时序）——只发图她不会回，用户只会对着一条没有回音的消息干等。
     * 上传进行中也不许再点：一条消息发两遍比发不出去更糟。
     */
    val canSend: Boolean
        get() = input.isNotBlank() && connection == CompanionConnection.CONNECTED && !uploading

    /** 当前是否有待确认的动作。有的话输入区要让位给确认按钮。 */
    val pendingConfirmation: String?
        get() = messages.lastOrNull { it.confirmationId != null }?.confirmationId

    /**
     * 是否应该显示"对方正在输入"。最后一条是她的空消息且还在流式接收，就是等待首字阶段。
     *
     * 「空」指**既没字也没图**：图片消息本来就可能一个字都没有（她在发一张照片），
     * 那时下面挂一条永远不消失的「对方正在输入…」等于说她还在说话——她已经说完了。
     */
    val awaitingReply: Boolean
        get() = messages.lastOrNull()?.let {
            it.role == MessageRole.ASSISTANT && it.text.isEmpty() && it.images.isEmpty()
        } == true
}
