package com.openapi.ks.myapp.companion

import com.openapi.ks.myapp.avatar.ProactiveKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * 房间内事件。
 *
 * 文字与语音共用一条通道：文字模式只有 [TextDelta] 与 [TurnDone]，语音模式额外
 * 有 [AvatarJson]（情绪、状态），将来加上音频振幅后也是同一条流。
 *
 * 主动事件（Proactive / NeedsConfirmation / IncomingCall）与普通回复走同一条流：
 * 「她主动找你」和「你跟她说话」在 agent 里是同一套逻辑，区别只在触发源，界面
 * 不应该为此开第二条连接。
 */
sealed interface CompanionEvent {
    data class TextDelta(val text: String) : CompanionEvent
    data class AvatarJson(val raw: String) : CompanionEvent
    data object TurnDone : CompanionEvent
    data class Error(val message: String) : CompanionEvent

    /** 她主动发起的一条消息（事件提醒、问候）。 */
    data class Proactive(val text: String, val kind: ProactiveKind) : CompanionEvent

    /** 需要用户确认才执行的动作。 */
    data class NeedsConfirmation(
        val confirmationId: String,
        val prompt: String,
    ) : CompanionEvent

    /** 主动来电。 */
    data object IncomingCall : CompanionEvent

    /**
     * 把当前表情收回立绘（B3）。降级进 L2 后端侧丢全部 presentation cue，卡在
     * 脸上的表情等不到下一条 emotion，只能由传输层在升档那条边主动发一次。
     * 与 [AvatarJson] 分开而不是复用空 emotion：协议里 `"emotion":""` 是「没有
     * 情绪」的哨兵（mapper 丢弃），承担不了「画面回立绘」这层意思。
     */
    data object ClearAvatarEmotion : CompanionEvent
}

/**
 * 房间连接状态。
 *
 * 与界面层的 [CompanionConnection] 分开：这里是「房间到了哪一步」的事实，
 * 界面层还要叠上自己的错误文案与展示口径，两者混用会让改文案变成改传输层。
 */
enum class TransportConnection { CONNECTING, CONNECTED, RECONNECTING, DISCONNECTED }

/**
 * 与 LiveKit 房间的连接。
 *
 * 抽成接口是为了让界面在没有房间、没有网络时也能完整跑通：默认注入
 * [FakeCompanionTransport]，真机联调时换成下面的 LiveKit 实现。
 */
interface CompanionTransport {
    /**
     * 房间连接状态。默认恒为 [TransportConnection.CONNECTED]：不关心建连过程的
     * 替身与测试不必实现它，界面照常可用。
     */
    val connection: StateFlow<TransportConnection>
        get() = defaultConnected

    /**
     * 她说话的瞬时振幅 0..1，用来驱动嘴型与通话页的呼吸环。
     *
     * 默认恒为 0：没有音频能力的替身不该假装她在说话。真实实现从远端音频轨的
     * PCM 回调里算 RMS——这是唯一能拿到「这一刻声音多大」的地方，文本流没有这个信息。
     */
    val mouthLevel: StateFlow<Float>
        get() = silentMouth

    /**
     * 她的声音的**真 PCM**（16kHz 单声道 PCM16LE，约 10ms 一块），给吃 PCM 的形象引擎
     * （Duix / Tao）做口型。默认空流：没有音频轨的替身不该编造语音。
     * 只保留最新的块（慢消费者丢旧的）：口型只关心「此刻」。
     */
    val remotePcm: Flow<ByteArray>
        get() = emptyPcm

    /**
     * **用户自己**说话的瞬时振幅 0..1，用来驱动通话页底部那条波形。
     *
     * 与 [mouthLevel] 分开：上面那个是她的声音（驱动呼吸环、嘴型），这个是你的声音
     * （驱动波形）。以前两者共用 mouthLevel，结果就是「你说话波形不动」——那个波形
     * 画的是她的音量，不是你自己的。
     *
     * 默认恒为 0；真实实现读本地麦克风采集轨的 PCM。
     */
    val selfLevel: StateFlow<Float>
        get() = silentMouth

    /**
     * **用户自己**声音的频段能量，[VoiceSpectrum.BANDS] 个 0..1 的值，低频在前。
     *
     * 与 [selfLevel] 同一个回调里算出：音量决定波形整体多高，频段决定形状——
     * 低沉和尖细的声音画出来不一样。默认空数组：没有采集能力的替身不该编造频谱，
     * 界面在空数组时只按音量画。
     */
    val selfBands: StateFlow<FloatArray>
        get() = noBands

    /**
     * 是否在出声。false 时调用方应当把远端音量降到 0（[setPlaybackAudible]）。
     *
     * 退出通话页只改变形态，房间并不断开——但用户的心智是「我挂断了，她不该还在说话」。
     * 所以退出时就静音播放，回来时恢复；麦克风同理。
     */
    fun setPlaybackAudible(audible: Boolean) = Unit

    suspend fun connect(session: SessionResponse)

    fun sendText(text: String)

    /**
     * 用户点了她一下。[region] 是命中的部位（[com.openapi.ks.myapp.avatar.AvatarRegion]）。
     *
     * 默认空实现：没有形象能力的替身不该假装能上报触摸。真实实现把它作为一条数据
     * 通道事件发给 agent——回不回、回什么由她决定，客户端不替她说话。
     */
    fun sendTouch(region: String) = Unit

    /**
     * 这条连接能不能发图片消息：`hello_ack` 的交集里 `media.image` 还在不在。
     *
     * 用 [StateFlow] 而不是普通 `Boolean`，因为协商发生在**连接之后**（hello 发出去、
     * ack 晚几百毫秒到），界面打开时还不知道答案。ViewModel 像接 [connection] 那样接它，
     * 谈成了「+」自己就出来；没谈成（老服务端 / 灰度关闭 / 还没握手）就一直不出现。
     *
     * 默认 false：没有协议协商能力的替身不该假装服务端收得了图——那等于给用户一个
     * 按下去什么都不会发生的「+」。
     */
    val imageSupported: StateFlow<Boolean>
        get() = noImageSupport

    /**
     * 把刚上传好的图片 id 交给房间（协议 `control.input`，`kind="attachments"`）。
     *
     * 与 [sendText] 是**两条独立的出站消息**：attachments 先到，服务端才有一条消息可以挂图；
     * 文本后到，才触发她的回合。少了前一条，她会说「图我看到了」而界面上根本没有图。
     *
     * **必须是 `suspend`，而且调用方必须 `await` 到它返回再发文本。** 这条契约钉的是
     * 「attachments 真正落进房间」这个事实，不是「我们先调用了它」：把两个发布各自
     * `launch` 到线程池上（[sendText] 现在的样子），谁先到达由调度器决定，而服务端的
     * pending 槽只活到**下一次**用户回合——文本抢跑一次，图片就落到下一条消息上去。
     * 等发布完成再发文本，顺序才是真的。
     *
     * [clientMsgId] 是这一组附件的幂等键（`att-<epochMillis>-<seq>`）：重连补发、
     * 或用户重复触发时，服务端凭它认得出「这是同一组」，不会挂两遍。
     *
     * **返回值就是这条契约的后半截**（终评 I-3）：`true` = 这组附件已经落进房间，可以发文本了；
     * `false` = 没能落进去（房间没了、协商在通话中间变了、编码失败、发布失败）。
     * 以前它返回 Unit 并且在每条失败路径上「记一条 warn 然后什么都不做」，调用方无从分辨
     * 成功与失败，于是网络一抖就是「文本发出去了、图没了、图也没留着」——正是这条接口
     * 自己声明要防的半条消息。调用方拿到 false 必须**不发文本**、把待发条原样留着。
     *
     * 空 `uploadIds` 返回 `true`（没什么要落的，也就没有失败）；调用方本来就不该用空集合进来。
     *
     * 默认实现返回 `false`：没有数据通道的替身**没有**这条路径，「没落进去」是它的真实答案
     * （发送侧的入口闸在 [imageSupported]，正常走不到这里）。
     */
    suspend fun sendAttachments(uploadIds: List<String>, clientMsgId: String): Boolean = false

    /**
     * 开关本地麦克风。默认空实现：没有音频能力的替身不该假装能开麦。
     * 真实实现由 SDK 负责发布/取消发布音轨，调用方只表达意图。
     */
    fun setMicEnabled(enabled: Boolean) = Unit

    /**
     * dh-lab 测试语音 rig 的注入入口（仅 debug 构建有调用方）：把 16kHz 单声道
     * PCM16LE 的一小块（~10ms）从**远端音频轨回调的同一个入口**送进来——电平提取、
     * [mouthLevel]、[remotePcm]、降级传感器全部原路复现，不为此开旁路接口。
     *
     * 返回 false = 这条连接没有可注入的音频通路（替身/未初始化），调用方自行降级。
     * 默认 false：没有音频轨的替身连「她在说话」都不该假装，更不该假装收得到测试音。
     */
    fun injectLabAudioPcm16k(batch: ByteArray): Boolean = false

    /**
     * 待确认动作的回执**不在这里**：它是用户的决定，走 HTTP
     * `POST /v1/actions/{id}/confirm`（[CompanionApi.confirmAction]），
     * 带鉴权、幂等与错误码，失败要让用户看见。
     *
     * 这里曾经有一个 `confirm(id, accepted)` 的空实现，但没有任何实现覆盖它、
     * 也没有任何调用方——确认按钮走的是 ViewModel 的 `resolveAction` → HTTP。
     * 留着它只会让人以为数据通道上还有一条回执路径。协议 v2 计划把控制面
     * 统一到 `cmp.ctl`，届时再按那个契约新增方法，而不是复活这个名字。
     */

    val events: Flow<CompanionEvent>

    suspend fun disconnect()
}

/** [CompanionTransport.connection] 的默认值：没有建连过程就等于一直连着。 */
private val defaultConnected: StateFlow<TransportConnection> =
    MutableStateFlow(TransportConnection.CONNECTED).asStateFlow()

/** [CompanionTransport.mouthLevel] 的默认值：没有音频轨就等于一直闭着嘴。 */
private val silentMouth: StateFlow<Float> = MutableStateFlow(0f).asStateFlow()

/** [CompanionTransport.remotePcm] 的默认值：没有音频轨就没有 PCM。 */
private val emptyPcm: Flow<ByteArray> = kotlinx.coroutines.flow.emptyFlow()

/** [CompanionTransport.selfBands] 的默认值：没有采集就没有频谱。 */
private val noBands: StateFlow<FloatArray> = MutableStateFlow(FloatArray(0)).asStateFlow()

/** [CompanionTransport.imageSupported] 的默认值：没谈成协议就是不能发图。 */
private val noImageSupport: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

/**
 * 测试与离线开发用的替身：逐词吐出一段固定回复，并在开头发一个情绪事件。
 *
 * 真实实现 [LiveKitCompanionTransport] 就按下面这些通道接入，这里列出来是为了
 * 让替身和真实现的边界一眼可见：
 *   1. connect() 里用 `session.token` 和 `session.livekitUrl` 建立 Room；
 *   2. 收：`companion.avatar` → AvatarJson、`companion.proactive` → Proactive、
 *      `companion.action` → NeedsConfirmation（见 `handleData`）；
 *   3. 发：sendText() 走 `lk.chat` 文本流，sendTouch() 走 `companion.touch`；
 *   4. 语音模式另外发布麦克风音轨，并订阅 agent 音轨做振幅驱动嘴型。
 *
 * **待确认动作的回执不在这条通道上**：它走 HTTP
 * `POST /v1/actions/{id}/confirm`（[CompanionApi.confirmAction]）。
 */
class FakeCompanionTransport(
    private val reply: String = "我在。慢慢说，先讲发生了什么。",
) : CompanionTransport {

    override suspend fun connect(session: SessionResponse) = Unit

    override fun sendText(text: String) = Unit

    override suspend fun disconnect() = Unit

    override val events: Flow<CompanionEvent> = flow {
        emit(CompanionEvent.AvatarJson("""{"v":1,"type":"state","state":"thinking"}"""))
        delay(320)
        emit(CompanionEvent.AvatarJson("""{"v":1,"type":"state","state":"speaking"}"""))
        emit(CompanionEvent.AvatarJson("""{"v":1,"type":"emotion","emotion":"calm"}"""))
        // 逐词吐，模拟流式：界面上的打字光标与断句都按真实节奏跑。
        reply.chunked(3).forEach {
            delay(90)
            emit(CompanionEvent.TextDelta(it))
        }
        emit(CompanionEvent.AvatarJson("""{"v":1,"type":"state","state":"idle"}"""))
        emit(CompanionEvent.TurnDone)
    }
}
