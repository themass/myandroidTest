package com.openapi.ks.myapp.companion.protocol

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 客户端接线层的行为（§5.1 / §7 / §14.2 / §14.3）。
 *
 * 纯逻辑（编解码、序号状态机、能力交集）已经由 [ProtocolCodecTest] 与
 * [ProtocolHandshakeTest] 锁住。这里锁的是**接线**：epoch 会不会换、回显会不会被
 * 当成对方说的话、伪造的消息会不会被挡、hello 是不是真的排在了序号 0。
 *
 * 这一层最容易出的错是「逻辑都对，但没人调用它」——服务端那次就是这么栽的：
 * 协议写得完整，房间里一个 `data_received` 处理器都没有。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ProtocolTransportTest {

    private val me = "user-1002"
    private val agent = "agent-linche"

    private fun helloAck(sid: String = "s-1", caps: Map<String, Int> = mapOf("presentation.emotion" to 1)): JSONObject =
        JSONObject()
            .put("sid", sid)
            .put("compat", "v2")
            .put("caps", JSONObject(caps as Map<*, *>))
            .put("limits", JSONObject().put("max_payload_bytes", 4096))

    /** 一封来自 agent 的 `cmp.cue` 消息的字节。 */
    private fun cueBytes(
        seq: Long = 0,
        ep: String = "ep-a",
        id: String = "m-$seq",
        eventType: String = "presentation.emotion",
    ): ByteArray = ProtocolCodec.encode(
        ProtocolCodec.newEnvelope(
            eventType = eventType,
            args = JSONObject().put("emotion", "开心"),
            seq = seq,
            ep = ep,
            id = id,
        ),
    )

    // ------------------------------------------------------------ 发出侧

    @Test
    fun `hello 排在 ctl 的序号 0 上`() {
        val link = CompanionProtocolLink(identity = me)
        val bytes = link.helloBytes()!!
        val envelope = ProtocolCodec.decode(bytes).envelope!!

        assertEquals(ProtocolCatalog.TOPIC_CTL, ProtocolCatalog.specOf(envelope.t)!!.topic)
        assertEquals("control.hello", envelope.t)
        assertEquals(0L, envelope.seq)
        assertEquals(link.epoch, envelope.ep)

        // 下一条才该是 1：握手占掉了序号 0，服务端从 0 起算，跳号会被记成 gap。
        val next = link.outgoing(ProtocolCatalog.TOPIC_CTL, "control.barge_in", JSONObject(), force = true)!!
        assertEquals(1L, ProtocolCodec.decode(next).envelope!!.seq)
    }

    @Test
    fun `重发 hello 序号递增 id 换新——服务端补记而不是当重复丢`() {
        // 实测的首连竞态：connect() 协程比 Connected 事件晚 2–4 秒恢复，赶不上
        // 服务端 §7.3 的 3 秒窗口，只能靠补发。补发要能被服务端接受，靠的就是
        // 这两条：seq 必须比第一条大（否则 STALE 丢弃），id 必须是新的
        // （否则 DUPLICATE 丢弃）。epoch 反而必须不变——换 epoch 会把对端的
        // 序号簿记整个清掉，那是重连才做的事。
        val link = CompanionProtocolLink(identity = me)
        val first = ProtocolCodec.decode(link.helloBytes()!!).envelope!!
        val retry = ProtocolCodec.decode(link.helloBytes()!!).envelope!!

        assertEquals("control.hello", retry.t)
        assertEquals(1L, retry.seq)
        assertNotEquals(first.id, retry.id)
        assertEquals(first.ep, retry.ep)
    }

    @Test
    fun `协商之前不发 cue`() {
        val link = CompanionProtocolLink(identity = me)
        // 没握手就没有能力表：猜一个会让对端发来我处理不了的消息。
        assertNull(link.outgoing(ProtocolCatalog.TOPIC_CUE, "presentation.emotion", JSONObject()))

        link.onAck(helloAck())
        assertTrue(link.isV2)
        assertNotNull(link.outgoing(ProtocolCatalog.TOPIC_CUE, "presentation.emotion", JSONObject()))
    }

    @Test
    fun `没协商到的能力不发`() {
        val link = CompanionProtocolLink(identity = me)
        link.onAck(helloAck(caps = mapOf("speech.v1" to 1)))
        assertNull(link.outgoing(ProtocolCatalog.TOPIC_CUE, "presentation.emotion", JSONObject()))
    }

    @Test
    fun `我自己发的事件不查能力表`() {
        // caps 描述的是「收信方能收什么」。telemetry.health 是客户端发、服务端收，
        // 它永远不会出现在交集里——用 canReceive 去问会永远得到 false，
        // 表现是客户端一个健康包也发不出去，而日志里什么错都没有。
        val link = CompanionProtocolLink(identity = me)
        link.onAck(helloAck(caps = mapOf("presentation.emotion" to 1)))
        assertFalse(link.negotiator.canReceive("telemetry.health"))

        val bytes = link.outgoing(
            ProtocolCatalog.TOPIC_TEL,
            "telemetry.health",
            JSONObject().put("received", 3),
        )
        assertNotNull(bytes)
        val envelope = ProtocolCodec.decode(bytes!!).envelope!!
        assertEquals(0L, envelope.seq)
        assertEquals(link.epoch, envelope.ep)
    }

    @Test
    fun `没谈成 v2 时我自己发的事件也不发`() {
        // 门控松的是「查不查能力表」，不是「要不要握手」。
        val link = CompanionProtocolLink(identity = me)
        assertNull(link.outgoing(ProtocolCatalog.TOPIC_TEL, "telemetry.health", JSONObject()))
    }

    @Test
    fun `目录里没有的事件不发`() {
        val link = CompanionProtocolLink(identity = me)
        link.onAck(helloAck())
        assertNull(link.outgoing(ProtocolCatalog.TOPIC_TEL, "telemetry.unknown", JSONObject()))
    }

    @Test
    fun `计数快照带上 expired 这个桶`() {
        // §14.3 的桶少一个，运维就少一条判断依据；而且它只在这一层暴露，
        // ProtocolReceiver 自己的字段不会自动冒上来。
        val link = CompanionProtocolLink(identity = me)
        val counters = link.counters()
        assertEquals(0, counters.droppedExpired)
        assertEquals(0, counters.received)
    }

    @Test
    fun `hello_ack 解析不出来就保持什么都收不到`() {
        val link = CompanionProtocolLink(identity = me)
        link.onAck(JSONObject())
        assertFalse(link.isV2)
        assertNull(link.negotiator.ack)
    }

    // ------------------------------------------------------------ 收侧

    @Test
    fun `agent 发来的一封 cue 会被采纳`() {
        val link = CompanionProtocolLink(identity = me)
        link.onAck(helloAck())

        val accepted = link.onIncoming(ProtocolCatalog.TOPIC_CUE, cueBytes(), agent, publisherIsAgent = true)
        assertNotNull(accepted)
        assertEquals("presentation.emotion", accepted!!.envelope.t)
        assertEquals(1, link.counters().applied)
    }

    @Test
    fun `另一个人类客户端伪造的感情会被挡`() {
        val link = CompanionProtocolLink(identity = me)
        link.onAck(helloAck())

        // 房间里被邀请的第三方也往 cmp.cue 发同一封包：方向对、topic 对、序号也对，
        // 唯一不对的是"谁发来的"。这就是 §14.2 那张表存在的理由。
        val accepted = link.onIncoming(ProtocolCatalog.TOPIC_CUE, cueBytes(), "another-user", publisherIsAgent = false)
        assertNull(accepted)
        assertEquals(1, link.counters().droppedUnauthorized)
        assertEquals(0, link.counters().applied)
    }

    @Test
    fun `我们自己发出去的回显不算她说的话`() {
        val link = CompanionProtocolLink(identity = me)
        link.onAck(helloAck())

        assertNull(link.onIncoming(ProtocolCatalog.TOPIC_CUE, cueBytes(), me, publisherIsAgent = false))
        assertEquals(1, link.counters().droppedUnauthorized)
    }

    @Test
    fun `没有发布者的包不会被当成她的`() {
        val link = CompanionProtocolLink(identity = me)
        link.onAck(helloAck())

        assertNull(link.onIncoming(ProtocolCatalog.TOPIC_CUE, cueBytes(), null, publisherIsAgent = true))
        assertEquals(1, link.counters().droppedUnauthorized)
    }

    @Test
    fun `服务端方向的事件从客户端来一律挡掉`() {
        val link = CompanionProtocolLink(identity = me)
        link.onAck(helloAck())

        // cmp.tel 只许客户端发；agent 发来的一律不是给我的。
        val accepted = link.onIncoming(ProtocolCatalog.TOPIC_TEL, cueBytes(), agent, publisherIsAgent = true)
        assertNull(accepted)
        assertEquals(1, link.counters().droppedUnauthorized)
    }

    @Test
    fun `不在 cmp 命名空间里的 topic 不走协议栈`() {
        val link = CompanionProtocolLink(identity = me)
        link.onAck(helloAck())

        assertNull(link.onIncoming("companion.avatar", cueBytes(), agent, publisherIsAgent = true))
        assertEquals(1, link.counters().droppedBad)
    }

    @Test
    fun `旧 topic 与协议 topic 不重名`() {
        // 两条路靠 topic 名区分。哪天有人把 companion.* 加进 TOPICS，v1 的分派就没了。
        val legacy = setOf("companion.avatar", "companion.proactive", "companion.action", "companion.touch")
        assertTrue(ProtocolCatalog.TOPICS.intersect(legacy).isEmpty())
    }

    @Test
    fun `坏包计入 bad 且不影响下一条`() {
        val link = CompanionProtocolLink(identity = me)
        link.onAck(helloAck())

        for (bad in listOf(ByteArray(0), "b{".toByteArray(), byteArrayOf(0xff.toByte(), 0xfe.toByte()))) {
            assertNull(link.onIncoming(ProtocolCatalog.TOPIC_CUE, bad, agent, publisherIsAgent = true))
        }
        assertEquals(3, link.counters().droppedBad)

        // 之后的好包照常进来：§14.1 一条失败不得影响后续。
        assertNotNull(link.onIncoming(ProtocolCatalog.TOPIC_CUE, cueBytes(), agent, publisherIsAgent = true))
        assertEquals(1, link.counters().applied)
    }

    // ------------------------------------------------------------ 定向与序号

    @Test
    fun `连上之前收不到定向消息连上之后能收到`() {
        val link = CompanionProtocolLink(identity = null)
        link.onAck(helloAck())

        val targeted = ProtocolCodec.encode(
            ProtocolCodec.newEnvelope(
                eventType = "presentation.emotion",
                args = JSONObject(),
                seq = 0,
                ep = "ep-a",
                id = "m-1",
                dst = listOf(me),
            ),
        )
        // 还不知道自己是谁：保守地当作不是给我的。
        assertNull(link.onIncoming(ProtocolCatalog.TOPIC_CUE, targeted, agent, publisherIsAgent = true))

        link.bindIdentity(me)
        assertNotNull(link.onIncoming(ProtocolCatalog.TOPIC_CUE, targeted, agent, publisherIsAgent = true))
    }

    @Test
    fun `bindIdentity 不会被 null 冲掉`() {
        val link = CompanionProtocolLink(identity = me)
        link.bindIdentity(null)
        link.bindIdentity("")
        assertEquals(me, link.identity)

        // 换一个真的值才生效。
        link.bindIdentity("user-2002")
        assertEquals("user-2002", link.identity)
    }

    @Test
    fun `重复的 id 被去重`() {
        val link = CompanionProtocolLink(identity = me)
        link.onAck(helloAck())

        assertNotNull(link.onIncoming(ProtocolCatalog.TOPIC_CUE, cueBytes(seq = 0, id = "x"), agent, true))
        assertNull(link.onIncoming(ProtocolCatalog.TOPIC_CUE, cueBytes(seq = 1, id = "x"), agent, true))
        assertEquals(1, link.counters().droppedDup)
    }

    @Test
    fun `跳号只计数不请求重传`() {
        val link = CompanionProtocolLink(identity = me)
        link.onAck(helloAck())

        link.onIncoming(ProtocolCatalog.TOPIC_CUE, cueBytes(seq = 0, id = "a"), agent, true)
        link.onIncoming(ProtocolCatalog.TOPIC_CUE, cueBytes(seq = 4, id = "b"), agent, true)
        assertEquals(1, link.counters().gaps)
        // 跳号的那条仍然要处理：表现指令的价值窗口太短，等重传更伤。
        assertEquals(2, link.counters().applied)
    }

    // ------------------------------------------------------------ 重连

    @Test
    fun `重连换 epoch 并且序号从 0 重来`() {
        val link = CompanionProtocolLink(identity = me)
        link.onAck(helloAck())
        val oldEpoch = link.epoch
        link.outgoing(ProtocolCatalog.TOPIC_CTL, "control.barge_in", JSONObject(), force = true)

        link.onReconnected()

        assertNotEquals(oldEpoch, link.epoch)
        // 协商结果属于上一条连接：新连接上服务端可能是另一个进程。
        assertFalse(link.isV2)
        val hello = ProtocolCodec.decode(link.helloBytes()!!).envelope!!
        assertEquals(0L, hello.seq)
        assertEquals(link.epoch, hello.ep)
    }

    @Test
    fun `重连后对端从 0 重发的消息不会被当成 stale`() {
        val link = CompanionProtocolLink(identity = me)
        link.onAck(helloAck())
        link.onIncoming(ProtocolCatalog.TOPIC_CUE, cueBytes(seq = 7, id = "m-7"), agent, true)

        link.onReconnected()
        link.onAck(helloAck(sid = "s-2"))

        // 不清表的话这条会被当成 seq 回退丢掉，用户看到的是「重连之后她突然不理我了」。
        assertNotNull(link.onIncoming(ProtocolCatalog.TOPIC_CUE, cueBytes(seq = 0, id = "m-0", ep = "ep-b"), agent, true))
        assertEquals(2, link.counters().applied)
    }

    @Test
    fun `epoch 每次都不一样`() {
        assertNotEquals(CompanionProtocolLink.newEpoch(), CompanionProtocolLink.newEpoch())
        assertEquals(16, CompanionProtocolLink.newEpoch().length)
    }

    // ------------------------------------------------------------ 中文

    @Test
    fun `中文按 UTF-8 上线路不带转义`() {
        val link = CompanionProtocolLink(identity = me)
        link.onAck(helloAck(caps = mapOf("presentation.emotion" to 1)))
        val bytes = link.outgoing(
            ProtocolCatalog.TOPIC_CUE,
            "presentation.emotion",
            JSONObject().put("emotion", "开心"),
        )!!

        assertTrue("开心".toByteArray().toList().let { needle -> indexOf(bytes, needle) >= 0 })
        assertFalse(String(bytes, Charsets.UTF_8).contains("\\u"))
    }

    private fun indexOf(haystack: ByteArray, needle: List<Byte>): Int {
        if (needle.isEmpty() || needle.size > haystack.size) return -1
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
