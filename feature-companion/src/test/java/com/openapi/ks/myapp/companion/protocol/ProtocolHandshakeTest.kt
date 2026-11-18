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
        val args = ProtocolHandshake.helloArgs(caps = mapOf("speech.timing" to 0))
        assertEquals(0, args.getJSONObject("caps").getInt("speech.timing"))
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
                caps = JSONObject().put("presentation.emotion", 1).put("speech.timing", 0),
                limits = JSONObject().put("max_payload_bytes", 1024),
            )
        )!!
        assertEquals("s-1", ack.sid)
        assertEquals("v2", ack.compat)
        assertEquals(1, ack.caps["presentation.emotion"])
        assertEquals(0, ack.caps["speech.timing"])
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
            ackJson(caps = JSONObject().put("speech.timing", 0))
        )!!
        assertFalse(ProtocolHandshake.supports(ack, "speech.timing"))
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

    @Test
    fun `握手超时是 3 秒`() {
        assertEquals(3_000L, ProtocolHandshake.ACK_TIMEOUT_MS)
    }
}
