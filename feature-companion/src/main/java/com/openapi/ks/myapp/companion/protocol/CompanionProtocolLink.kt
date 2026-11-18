package com.openapi.ks.myapp.companion.protocol

import org.json.JSONObject
import java.util.UUID

/**
 * 客户端这一侧的协议接线状态：epoch、序号、协商结果、收侧计数（§5 / §7 / §14.2）。
 *
 * 纯 Kotlin，不碰 LiveKit 与协程：真正的房间事件在
 * [com.openapi.ks.myapp.companion.LiveKitCompanionTransport] 里，这里只回答两个问题——
 * "这条包要不要处理"和"我要发出去的这条该带什么 seq"。
 *
 * **epoch 每次连接换一个**（§5.1）。它不是调试信息：对端靠它判断"你这条流的序号
 * 和我记的能不能比"。重连后沿用旧 epoch，对端会把我从 0 开始的 seq 当成回退丢掉。
 */
class CompanionProtocolLink(
    identity: String? = null,
    epoch: String = newEpoch(),
    private val newId: () -> String = ::newId,
) {

    /**
     * 我在这条连接上的 identity。`null` 表示"还不知道"——此时只收广播，
     * 定向消息（`dst` 非空）一律当作不是给我的。
     *
     * 建 `Room` 时 SDK 还没分配 identity，所以先传 null，连上之后用
     * [bindIdentity] 补上。判"是不是自己发的回声"也靠它：为 null 时那条检查
     * 什么也挡不住，只能靠 SDK 不把自家包回显给我们。
     */
    var identity: String? = identity
        private set

    /** 当前连接的 epoch。重连必换（§5.1），见 [onReconnected]。 */
    var epoch: String = epoch
        private set

    val receiver = ProtocolReceiver(identity)

    /** 协商结果。没握到手时是"什么都收不到"——猜一个能力集会让对端发来收不了的消息。 */
    var negotiator: ProtocolHandshake.Negotiator = ProtocolHandshake.Negotiator(null)
        private set

    val isV2: Boolean get() = negotiator.isV2

    /** 每个 topic 一条独立序号空间（§5.1）。`seq` 是 64 位。 */
    private val seqByTopic = HashMap<String, Long>()

    // 信封层就被挡掉的那些，`ProtocolReceiver` 看不到，只能在这边记。两者相加才是全部。
    private var rejectedBad = 0
    private var rejectedUnauthorized = 0
    private var rejectedUnknown = 0

    /** 一条已经通过全部校验、应当交给上层的信。 */
    data class Accepted(val envelope: ProtocolCodec.Envelope, val topic: String)

    /**
     * 连上之后补上自己的 identity。
     *
     * `null`（SDK 还没给）不覆盖已有的值：identity 在一条连接内不会变，拿 null 去
     * 冲掉一个已知的值只会让后续的 `dst` 判断全部失效。
     */
    fun bindIdentity(value: String?) {
        if (value.isNullOrBlank()) return
        identity = value
        receiver.identity = value
    }

    /** §14.3 的计数快照。运维靠这些桶区分"是网络还是我的编译器"。 */
    data class Counters(
        val received: Int,
        val applied: Int,
        val droppedDup: Int,
        val droppedStale: Int,
        val droppedExpired: Int,
        val droppedUnknown: Int,
        val droppedUnauthorized: Int,
        val droppedBad: Int,
        val gaps: Int,
    )

    fun counters(): Counters = Counters(
        received = receiver.received + rejectedBad + rejectedUnauthorized + rejectedUnknown,
        applied = receiver.applied,
        droppedDup = receiver.droppedDup,
        droppedStale = receiver.droppedStale,
        droppedExpired = receiver.droppedExpired,
        droppedUnknown = receiver.droppedUnknown + rejectedUnknown,
        droppedUnauthorized = receiver.droppedUnauthorized + rejectedUnauthorized,
        droppedBad = receiver.droppedBad + rejectedBad,
        gaps = receiver.state.gaps,
    )

    // ------------------------------------------------------------------ 收

    /**
     * 判定一条 `cmp.*` 数据包。返回 null 表示不该处理（已计入对应的丢弃桶）。
     *
     * 顺序与服务端一致：先解信封（坏包的形状值得统计，哪怕它来自不该发言的人），
     * 再看方向，再看发布者身份，最后才进序号状态机。
     *
     * `publisherIsAgent` 是 §14.2 在客户端这一侧的落点：房间是共享的，任何参与者
     * 都能往同一个 room 发数据包。只认方向不认身份，等于允许另一个客户端伪造一条
     * `presentation.emotion`，或者伪造 `control.cancel` 把我的一句话掐断。
     */
    fun onIncoming(
        topic: String?,
        data: ByteArray,
        publisher: String?,
        publisherIsAgent: Boolean,
    ): Accepted? {
        val result = ProtocolCodec.decode(data)
        val envelope = result.envelope
        if (envelope == null) {
            if (result.status == ProtocolCodec.Status.UNKNOWN_TYPE) rejectedUnknown++ else rejectedBad++
            return null
        }

        if (topic == null || topic !in ProtocolCatalog.TOPICS) {
            // 四个 topic 是 v2 的唯一入口。没带 topic 的包不可能是 v2 的信封。
            rejectedBad++
            return null
        }

        // 我们自己发出去又被回显的包，永远不是对方说的话。
        if (publisher == null || publisher == identity) {
            rejectedUnauthorized++
            return null
        }

        if (!protocolPublisherAllowed(envelope.t, publisherIsAgent)) {
            rejectedUnauthorized++
            return null
        }

        if (!ProtocolCatalog.allowed(topic, envelope.t, ProtocolCatalog.Side.CLIENT)) {
            rejectedUnauthorized++
            return null
        }

        return when (receiver.observe(envelope, publisher, topic)) {
            ProtocolReceiver.Verdict.ACCEPT -> Accepted(envelope, topic)
            ProtocolReceiver.Verdict.DUPLICATE,
            ProtocolReceiver.Verdict.STALE,
            ProtocolReceiver.Verdict.NOT_FOR_ME,
            -> null
        }
    }

    /**
     * 哪些 `t` 只能来自 agent 进程。
     *
     * `cmp.cue` 全体与 `cmp.ctl` 里的服务端方向事件都必须由 agent 发；`cmp.in` /
     * `cmp.tel` 是客户端发出去的，本来就到不了这里（方向白名单先挡）。
     *
     * 这里用 `kind == AGENT` 而不是"identity 白名单字符串"：identity 由建房间的一方
     * 决定，而 kind 由 LiveKit 在参与者入会时判定，客户端改不了。
     */
    private fun protocolPublisherAllowed(eventType: String, publisherIsAgent: Boolean): Boolean {
        val spec = ProtocolCatalog.specOf(eventType) ?: return false
        if (ProtocolCatalog.Side.SERVER !in spec.senders) return false
        return publisherIsAgent
    }

    // ------------------------------------------------------------------ 发

    /**
     * 组一封外发的信，返回要发出去的字节；null 表示按当前协商不该发。
     *
     * 门控分两种，因为 `caps` 描述的是「**收信方**能收什么」：
     *
     * * 收向事件（`presentation.*` / `speech.*`：服务端发、我们收）必须查协商表——
     *   没协商到的能力发出去对方也处理不了。
     * * 发向事件（`telemetry.health` / `perception.attention`：我们发、服务端收）不查
     *   能力表。`SERVER_CAPS` 里根本没有它们，用 `supports` 去问会永远得到 false；
     *   而且问的方向也不对——该问的是「对端收不收」，那由服务端的目录与分派决定，
     *   客户端无从也不该替它猜。这里只要求这条连接确实谈成了 v2。
     *
     * `force` 只给握手本身用：`control.hello` 必须在协商之前发出去，否则永远谈不成。
     */
    fun outgoing(
        topic: String,
        eventType: String,
        args: JSONObject,
        dst: List<String>? = null,
        force: Boolean = false,
    ): ByteArray? {
        if (!force && !maySendNow(eventType)) return null
        val seq = seqByTopic[topic] ?: 0L
        val envelope = ProtocolCodec.newEnvelope(
            eventType = eventType,
            args = args,
            seq = seq,
            ep = epoch,
            id = newId(),
            dst = dst,
        )
        val bytes = try {
            ProtocolCodec.encode(envelope)
        } catch (e: IllegalArgumentException) {
            // 超限是调用方的 bug，不是网络的错：不发出去，也不吞掉。
            return null
        }
        seqByTopic[topic] = seq + 1
        return bytes
    }

    private fun maySendNow(eventType: String): Boolean {
        if (!negotiator.isV2) return false
        val spec = ProtocolCatalog.specOf(eventType) ?: return false
        // 我发的：只要对方也谈成了 v2。收向的：还要这一项真的在交集里。
        if (ProtocolCatalog.Side.CLIENT !in spec.receivers) return true
        return negotiator.canReceive(eventType)
    }

    /** `control.hello` 的字节。握手是 v2 的唯一入口，所以不走能力协商。 */
    fun helloBytes(
        client: ProtocolHandshake.ClientInfo = ProtocolHandshake.ClientInfo(),
    ): ByteArray? = outgoing(
        topic = ProtocolCatalog.TOPIC_CTL,
        eventType = "control.hello",
        args = ProtocolHandshake.helloArgs(client = client),
        force = true,
    )

    /** 收到 `control.hello_ack` 后采纳协商结果。args 解析不出来就保持"什么都没谈成"。 */
    fun onAck(args: JSONObject) {
        negotiator = ProtocolHandshake.Negotiator(ProtocolHandshake.parseAck(args))
    }

    /**
     * 重连：清空全部收侧簿记、换 epoch、序号从 0 重来（§5.1 / F4）。
     *
     * 不清的话，重连后对端从 0 重发的消息会被当成 stale 丢掉——表现是"重连之后
     * 她突然不理我了"，而两端日志都是干净的。
     *
     * 协商结果也一起作废：新连接上服务端可能换了一个进程，旧的 sid 与 caps 描述的是
     * 上一条连接。调用方拿到新的 epoch 后要重发一次 hello。
     */
    fun onReconnected(): String {
        receiver.forgetAll()
        seqByTopic.clear()
        negotiator = ProtocolHandshake.Negotiator(null)
        epoch = newEpoch()
        return epoch
    }

    companion object {
        fun newEpoch(): String = UUID.randomUUID().toString().replace("-", "").take(16)

        fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)
    }
}
