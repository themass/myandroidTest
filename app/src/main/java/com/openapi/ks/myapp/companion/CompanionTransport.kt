package com.openapi.ks.myapp.companion

import com.openapi.ks.myapp.avatar.ProactiveKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
 * 与 LiveKit 房间的连接。
 *
 * 抽成接口是为了让界面在没有房间、没有网络时也能完整跑通：默认注入
 * [FakeCompanionTransport]，真机联调时换成下面的 LiveKit 实现。
 */
interface CompanionTransport {
    suspend fun connect(session: SessionResponse)

    fun sendText(text: String)

    /**
     * 回应一个待确认动作。走同一条通道而不是另开 HTTP：确认是对话的一部分，
     * 它要和触发它的那条消息落在同一个上下文里。
     */
    fun confirm(confirmationId: String, accepted: Boolean) = Unit

    val events: Flow<CompanionEvent>

    suspend fun disconnect()
}

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
