package com.openapi.ks.myapp.companion.protocol

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 握手与能力协商（§7 / §12.1）。
 *
 * 这一层最容易出的错不是异常，而是**静默地少协商一项**：客户端之后再也收不到
 * `presentation.emotion`，而两边日志都干净。所以这里逐条断言交集表的内容。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ProtocolHandshakeTest {

    // ------------------------------------------------------------ hello 组装

    @Test
    fun `hello 报的是版本区间而不是单值`() {
        val args = ProtocolHandshake.helloArgs()
        val proto = args.getJSONObject("proto")
        assertEquals(2, proto.getInt("min"))
        assertEquals(2, proto.getInt("max"))
    }

    @Test
    fun `hello 带上能力表与上限与客户端身份`() {
        val args = ProtocolHandshake.helloArgs(
            client = ProtocolHandshake.ClientInfo(app = "companion", version = "1.0", sdk = "2.29.0"),
        )
        assertEquals(
            ProtocolHandshake.CLIENT_CAPS.keys,
            args.getJSONObject("caps").keys().asSequence().toSet(),
        )
        assertEquals(
            ProtocolCodec.MAX_PAYLOAD_BYTES,
            args.getJSONObject("limits").getInt("max_payload_bytes"),
        )
        assertEquals("companion", args.getJSONObject("client").getString("app"))
    }

    @Test
    fun `hello 里值为 0 的能力也要报上去`() {
        val args = ProtocolHandshake.helloArgs(caps = mapOf("speech.v1" to 0))
        assertEquals(0, args.getJSONObject("caps").getInt("speech.v1"))
    }

    // ------------------------------------------------------------ ack 解析

    private fun ackJson(
        sid: String = "s-1",
        caps: JSONObject = JSONObject(),
        limits: JSONObject = JSONObject(),
        compat: String = "v2",
    ) = JSONObject()
        .put("sid", sid)
        .put("caps", caps)
        .put("limits", limits)
        .put("compat", compat)

    @Test
    fun `解析正常的 ack`() {
        val ack = ProtocolHandshake.parseAck(
            ackJson(
                caps = JSONObject().put("presentation.emotion", 1).put("speech.v1", 0),
                limits = JSONObject().put("max_payload_bytes", 1024),
            )
        )!!
        assertEquals("s-1", ack.sid)
        assertEquals("v2", ack.compat)
        assertEquals(1, ack.caps["presentation.emotion"])
        assertEquals(0, ack.caps["speech.v1"])
        assertEquals(1024, ack.limits["max_payload_bytes"])
    }

    @Test
    fun `缺 sid 的 ack 不可用`() {
        assertNull(ProtocolHandshake.parseAck(ackJson(sid = "")))
    }

    @Test
    fun `缺 compat 的 ack 不可用`() {
        assertNull(ProtocolHandshake.parseAck(ackJson(compat = "")))
    }

    @Test
    fun `缺 caps 的 ack 不可用`() {
        val args = JSONObject().put("sid", "s").put("compat", "v2").put("limits", JSONObject())
        assertNull(ProtocolHandshake.parseAck(args))
    }

    @Test
    fun `负的能力版本不可用`() {
        assertNull(
            ProtocolHandshake.parseAck(
                ackJson(caps = JSONObject().put("presentation.gaze", -1))
            )
        )
    }

    @Test
    fun `未协商出的类型一律不支持`() {
        val ack = ProtocolHandshake.parseAck(
            ackJson(caps = JSONObject().put("presentation.state", 1))
        )!!
        assertTrue(ProtocolHandshake.supports(ack, "presentation.state"))
        assertFalse(ProtocolHandshake.supports(ack, "presentation.emotion"))
        assertFalse(ProtocolHandshake.supports(ack, "presentation.teleport"))
    }

    @Test
    fun `值为 0 的能力视为未启用`() {
        val ack = ProtocolHandshake.parseAck(
            ackJson(caps = JSONObject().put("speech.v1", 0))
        )!!
        assertFalse(ProtocolHandshake.supports(ack, "speech.v1"))
    }

    @Test
    fun `speech 事件按 speech 能力协商而不是按自己的名字`() {
        val ack = ProtocolHandshake.parseAck(
            ackJson(caps = JSONObject().put("speech.v1", 1))
        )!!
        for (name in listOf("speech.seg", "speech.end", "control.cancel")) {
            assertTrue("$name 应该随 speech.v1 一起谈成", ProtocolHandshake.supports(ack, name))
        }
        // 反向：能力为 0 时，这三个都必须收不到。
        val off = ProtocolHandshake.parseAck(ackJson(caps = JSONObject().put("speech.v1", 0)))!!
        for (name in listOf("speech.seg", "speech.end", "control.cancel")) {
            assertFalse("$name 不该在 speech.v1 为 0 时可用", ProtocolHandshake.supports(off, name))
        }
    }

    @Test
    fun `客户端能力映射覆盖服务端的同一批事件`() {
        // 服务端 CAP_FOR_EVENT 也把这三个挂在 speech.v1 下；两侧不一致会让
        // 「服务端愿意发、客户端以为收不到」或反过来。
        assertEquals(
            setOf("speech.seg", "speech.end", "control.cancel"),
            ProtocolHandshake.CAP_FOR_EVENT.keys,
        )
        for (cap in ProtocolHandshake.CAP_FOR_EVENT.values) {
            assertTrue("$cap 必须在 CLIENT_CAPS 里", cap in ProtocolHandshake.CLIENT_CAPS)
        }
    }

    @Test
    fun `v1 连接收不到任何 cmp 事件`() {
        val ack = ProtocolHandshake.parseAck(
            ackJson(caps = JSONObject().put("presentation.emotion", 1), compat = "v1")
        )!!
        for (name in ProtocolHandshake.CLIENT_CAPS.keys) {
            assertFalse("$name 不该在 v1 连接上可用", ProtocolHandshake.supports(ack, name))
        }
    }

    // ------------------------------------------------------------ Negotiator

    @Test
    fun `没有 ack 时 Negotiator 什么都不支持`() {
        val n = ProtocolHandshake.Negotiator(null)
        assertFalse(n.isV2)
        for (name in ProtocolHandshake.CLIENT_CAPS.keys) {
            assertFalse(n.canReceive(name))
        }
        // 退回到硬限，而不是 0：0 会让所有发送都失败。
        assertEquals(ProtocolCodec.MAX_PAYLOAD_BYTES, n.maxPayloadBytes)
    }

    @Test
    fun `Negotiator 读服务端给的上限`() {
        val ack = ProtocolHandshake.parseAck(
            ackJson(
                caps = JSONObject().put("presentation.emotion", 1),
                limits = JSONObject().put("max_payload_bytes", 2048),
            )
        )!!
        val n = ProtocolHandshake.Negotiator(ack)
        assertTrue(n.isV2)
        assertTrue(n.canReceive("presentation.emotion"))
        assertEquals(2048, n.maxPayloadBytes)
    }

    @Test
    fun `客户端能力表与服务端覆盖同一批名字`() {
        // 这一条是软约束：两边都认识同一批名字，交集才不会凭空少项。
        // 真正的锁在共享向量里（decode 的 hello 用例）。
        for (name in ProtocolHandshake.CLIENT_CAPS.keys) {
            assertTrue("空能力名", name.isNotEmpty())
        }
        assertTrue(ProtocolHandshake.CLIENT_CAPS.size >= 5)
    }

    /**
     * 图片消息（P1）的协商位。
     *
     * 名字必须与服务端 `SERVER_CAPS` **逐字**一致——差一个字符不会报错，只会让交集里
     * 少这一项，于是「media.image」永远是 0，客户端的「+」入口永远不出现，而两边日志
     * 都是干净的（§7 交集取低版本的经典坑）。改这边要同步改
     * `companion/agent/protocol/handshake.py`。
     */
    @Test
    fun `图片能力以 media image 这个名字报上去`() {
        assertEquals(1, ProtocolHandshake.CLIENT_CAPS["media.image"])
        assertEquals(1, ProtocolHandshake.helloArgs().getJSONObject("caps").getInt("media.image"))
    }

    /** 谈成了才支持；服务端把它压成 0（灰度关闭）时这一项必须算未启用。 */
    @Test
    fun `media image 按协商结果决定而不是按客户端自己认识`() {
        val on = ProtocolHandshake.parseAck(ackJson(caps = JSONObject().put("media.image", 1)))!!
        assertTrue(ProtocolHandshake.supports(on, "media.image"))
        val off = ProtocolHandshake.parseAck(ackJson(caps = JSONObject().put("media.image", 0)))!!
        assertFalse("服务端给 0 就是关掉了", ProtocolHandshake.supports(off, "media.image"))
        val missing = ProtocolHandshake.parseAck(ackJson(caps = JSONObject()))!!
        assertFalse("老服务端根本不知道这一项", ProtocolHandshake.supports(missing, "media.image"))
        // v1 连接上任何 cmp 能力都不作数（与上面那条 v1 用例同一条规则）。
        val v1 = ProtocolHandshake.parseAck(ackJson(caps = JSONObject().put("media.image", 1), compat = "v1"))!!
        assertFalse(ProtocolHandshake.supports(v1, "media.image"))
    }

    @Test
    fun `握手超时是 3 秒`() {
        assertEquals(3_000L, ProtocolHandshake.ACK_TIMEOUT_MS)
    }
}
