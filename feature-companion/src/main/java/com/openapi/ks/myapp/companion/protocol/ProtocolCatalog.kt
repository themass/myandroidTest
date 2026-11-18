package com.openapi.ks.myapp.companion.protocol

/**
 * 协议 v2 的事件目录：每个 `t` 属于哪个 topic、哪个投递类、哪个方向。
 *
 * 与服务端 `companion/agent/protocol/catalog.py` 是同一张表。两端各写一份是因为
 * 它们不能共享代码；**内容一致性由 `tests/resources/protocol_v2.json` 那份共享
 * 向量在测试里锁住**，改一侧不改另一侧会让 `ProtocolCodecTest` 红。
 *
 * 方向校验在这里是安全边界（§14.2）：房间是多人共享的，任何人都能往同一个 room
 * 发数据包。不校验方向就等于允许别人伪造一条 `presentation.emotion`，或者伪造
 * `control.response` 去替用户应答一次确认。
 */
object ProtocolCatalog {

    /** 投递语义三分类（§6）。真正决定走 RELIABLE 还是 LOSSY 的是 topic。 */
    enum class Delivery { MUST, DEADLINE, DROPPABLE }

    /** 一条消息的「这一端」。 */
    enum class Side { SERVER, CLIENT }

    const val TOPIC_CUE = "cmp.cue"
    const val TOPIC_CTL = "cmp.ctl"
    const val TOPIC_IN = "cmp.in"
    const val TOPIC_TEL = "cmp.tel"

    val TOPICS: Set<String> = setOf(TOPIC_CUE, TOPIC_CTL, TOPIC_IN, TOPIC_TEL)

    /**
     * 一个 `t` 的静态属性。
     *
     * `senders` / `receivers` 分开写而不是合成一个「方向」枚举：`control.ping`
     * 两个方向都合法，但它仍然只属于 `cmp.ctl`；用一个集合表达双向比给枚举加一个
     * `BOTH` 成员更直白。
     */
    data class EventSpec(
        val topic: String,
        val delivery: Delivery,
        val senders: Set<Side>,
        val receivers: Set<Side>,
    )

    private fun oneWay(topic: String, delivery: Delivery, from: Side): EventSpec {
        val other = if (from == Side.SERVER) Side.CLIENT else Side.SERVER
        return EventSpec(topic, delivery, setOf(from), setOf(other))
    }

    private fun bothWays(topic: String, delivery: Delivery) =
        EventSpec(topic, delivery, Side.entries.toSet(), Side.entries.toSet())

    private val SERVER = Side.SERVER
    private val CLIENT = Side.CLIENT

    val EVENTS: Map<String, EventSpec> = mapOf(
        // ---- 控制面（cmp.ctl）----
        "control.hello" to oneWay(TOPIC_CTL, Delivery.MUST, CLIENT),
        "control.hello_ack" to oneWay(TOPIC_CTL, Delivery.MUST, SERVER),
        "control.ping" to bothWays(TOPIC_CTL, Delivery.MUST),
        "control.pong" to bothWays(TOPIC_CTL, Delivery.MUST),
        "control.cancel" to oneWay(TOPIC_CTL, Delivery.MUST, SERVER),
        "control.request" to oneWay(TOPIC_CTL, Delivery.MUST, SERVER),
        "control.response" to oneWay(TOPIC_CTL, Delivery.MUST, CLIENT),
        "control.barge_in" to oneWay(TOPIC_CTL, Delivery.MUST, CLIENT),
        // 触摸语义走数据包而不是文本流：建流的开销不值得（§15 迁移表）。
        "control.input" to oneWay(TOPIC_IN, Delivery.MUST, CLIENT),
        // ---- 表现面（cmp.cue）----
        "speech.begin" to oneWay(TOPIC_CUE, Delivery.MUST, SERVER),
        "speech.segment" to oneWay(TOPIC_CUE, Delivery.DEADLINE, SERVER),
        "speech.end" to oneWay(TOPIC_CUE, Delivery.MUST, SERVER),
        "presentation.state" to oneWay(TOPIC_CUE, Delivery.DEADLINE, SERVER),
        "presentation.emotion" to oneWay(TOPIC_CUE, Delivery.DEADLINE, SERVER),
        "presentation.gesture" to oneWay(TOPIC_CUE, Delivery.DEADLINE, SERVER),
        "presentation.gaze" to oneWay(TOPIC_CUE, Delivery.DEADLINE, SERVER),
        "presentation.resync" to oneWay(TOPIC_CUE, Delivery.MUST, SERVER),
        // ---- 感知与遥测（cmp.tel）----
        "perception.attention" to oneWay(TOPIC_TEL, Delivery.DROPPABLE, CLIENT),
        "telemetry.health" to oneWay(TOPIC_TEL, Delivery.DROPPABLE, CLIENT),
    )

    /** 按 `t` 取属性；不认识返回 null（调用方据此计 `dropped_unknown`）。 */
    fun specOf(eventType: String): EventSpec? = EVENTS[eventType]

    /**
     * 这一端能不能收这条消息（§14.2 方向白名单）。
     *
     * 三重条件缺一不可：topic 在白名单里、`t` 认识、且这个 `t` 确实属于这个 topic
     * 并且是发给这一侧的。第二与第三合在一起挡的是「方向对但 topic 不对」——例如
     * 有人往 `cmp.in` 上塞一条 cue。
     */
    fun allowed(topic: String, eventType: String, receiving: Side): Boolean {
        val spec = EVENTS[eventType] ?: return false
        if (spec.topic != topic) return false
        return receiving in spec.receivers
    }

    /** 这一端能不能发这条消息。发送侧断言用；接收侧的 [allowed] 才是安全边界。 */
    fun maySend(topic: String, eventType: String, sending: Side): Boolean {
        val spec = EVENTS[eventType] ?: return false
        if (spec.topic != topic) return false
        return sending in spec.senders
    }
}
