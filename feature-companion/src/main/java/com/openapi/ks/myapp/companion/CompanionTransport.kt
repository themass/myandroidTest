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
     * 开关本地麦克风。默认空实现：没有音频能力的替身不该假装能开麦。
     * 真实实现由 SDK 负责发布/取消发布音轨，调用方只表达意图。
     */
    fun setMicEnabled(enabled: Boolean) = Unit

    /**
     * 回应一个待确认动作。走同一条通道而不是另开 HTTP：确认是对话的一部分，
     * 它要和触发它的那条消息落在同一个上下文里。
     */
    fun confirm(confirmationId: String, accepted: Boolean) = Unit

    val events: Flow<CompanionEvent>

    suspend fun disconnect()
}

/** [CompanionTransport.connection] 的默认值：没有建连过程就等于一直连着。 */
private val defaultConnected: StateFlow<TransportConnection> =
    MutableStateFlow(TransportConnection.CONNECTED).asStateFlow()

/** [CompanionTransport.mouthLevel] 的默认值：没有音频轨就等于一直闭着嘴。 */
private val silentMouth: StateFlow<Float> = MutableStateFlow(0f).asStateFlow()

/**
 * 测试与离线开发用的替身：逐词吐出一段固定回复，并在开头发一个情绪事件。
 *
 * 接入真实 LiveKit 的位置在这里：
 *   1. connect() 里用 `session.token` 和 `session.livekitUrl` 建立 Room；
 *   2. 订阅 agent 的文本流（数据通道 topic 暂定 `companion.text`），
 *      每段 base64 或 JSON 解成 TextDelta；
 *   3. `companion.event` topic 上收到的事件透传为 AvatarJson；
 *   4. sendText() 向 `companion.input` 发送用户消息；
 *   5. `companion.proactive` topic 上是主动消息，`companion.action` 上是待确认
 *      动作，分别解成 Proactive / NeedsConfirmation；
 *   6. confirm() 向 `companion.confirm` 发 {confirmation_id, accepted}；
 *   7. 语音模式另外发布麦克风音轨，并订阅 agent 音轨做振幅驱动嘴型。
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
