package com.openapi.ks.myapp.companion.protocol

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest

/**
 * 协议 v2 信封编解码。
 *
 * 向量从 `src/test/resources/protocol_v2.json` 读，服务端读的是同一份内容的拷贝
 * （`server/tests/fixtures/protocol_v2.json`）。两端跑同一份向量，是唯一能挡住
 * 「各写各理解」的手段。
 *
 * 用 Robolectric 是因为 `org.json` 在纯 JVM 上是空壳桩，所有方法抛
 * `RuntimeException("Stub!")`。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ProtocolCodecTest {

    /**
     * fixture 的 sha256。服务端 `test_protocol_codec.py` 里有一模一样的常量。
     * 谁改了契约却只改一边，另一边的测试立刻红。
     */
    private val testVectorSha256 =
        "70464b8a66b5687e5b57c27e156e10998fb8627345abe1bdf99d91f654a487f5"

    private val vectors: JSONObject by lazy {
        val stream = javaClass.classLoader!!.getResourceAsStream("protocol_v2.json")
            ?: error("protocol_v2.json 不在 test resources 里")
        val text = stream.use { it.readBytes().toString(Charsets.UTF_8) }
        JSONObject(text)
    }

    private fun fixtureBytes(): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("protocol_v2.json")!!
            .use { it.readBytes() }

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    // ------------------------------------------------------------ 向量驱动

    private fun payload(case: JSONObject): ByteArray {
        case.optString("raw").takeIf { case.has("raw") }?.let {
            return it.toByteArray(Charsets.UTF_8)
        }
        val obj = JSONObject(case.getJSONObject("json").toString())
        if (!case.has("pad_to_bytes")) {
            return obj.toString().toByteArray(Charsets.UTF_8)
        }
        val target = case.getInt("pad_to_bytes")
        // 先算出不加填充的体积，再把差额塞进 args.pad。
        val args = obj.getJSONObject("args")
        args.put("pad", "")
        val base = obj.toString().toByteArray(Charsets.UTF_8).size
        args.put("pad", "x".repeat(target - base))
        val data = obj.toString().toByteArray(Charsets.UTF_8)
        assertEquals("padding arithmetic is off for ${case.getString("name")}", target, data.size)
        return data
    }

    @Test
    fun `fixture 与服务端那份逐字节一致`() {
        assertEquals(testVectorSha256, digest(fixtureBytes()))
    }

    @Test
    fun `解码向量全部符合预期`() {
        val cases = vectors.getJSONArray("decode")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val result = ProtocolCodec.decode(payload(case))
            assertEquals(
                "case: ${case.getString("name")}",
                case.getString("outcome"),
                result.status.name.lowercase(),
            )
            if (result.status == ProtocolCodec.Status.OK) {
                val json = case.getJSONObject("json")
                val envelope = result.envelope!!
                assertEquals(json.getString("t"), envelope.t)
                assertEquals(json.getString("id"), envelope.id)
                assertEquals(json.getLong("seq"), envelope.seq)
                assertEquals(json.getString("ep"), envelope.ep)
            }
        }
    }

    @Test
    fun `方向白名单向量全部符合预期`() {
        val cases = vectors.getJSONArray("direction")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val side = when (case.getString("role")) {
                "server" -> ProtocolCatalog.Side.SERVER
                else -> ProtocolCatalog.Side.CLIENT
            }
            val allowed = ProtocolCatalog.allowed(case.getString("topic"), case.getString("t"), side)
            assertEquals(
                "case: ${case.getString("name")}",
                case.getString("outcome"),
                if (allowed) "ok" else "wrong_direction",
            )
        }
    }

    @Test
    fun `收侧状态机向量全部符合预期`() {
        val cases = vectors.getJSONArray("receiver")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val receiver = ProtocolReceiver(if (case.has("me")) case.getString("me") else null)
            val expect = case.getJSONArray("expect")
            val ops = case.getJSONArray("ops")
            var expectedIndex = 0

            for (j in 0 until ops.length()) {
                val op = ops.getJSONObject(j)
                if (op.optString("op") == "forget") {
                    receiver.forgetPublisher(op.getString("publisher"))
                    continue
                }
                val dst = op.optJSONArray("dst")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                }
                val envelope = ProtocolCodec.newEnvelope(
                    eventType = "presentation.emotion",
                    args = JSONObject(),
                    seq = op.getLong("seq"),
                    ep = op.getString("ep"),
                    id = op.getString("id"),
                    dst = dst,
                )
                val verdict = receiver.observe(envelope, op.getString("publisher"), op.getString("topic"))
                assertEquals(
                    "case: ${case.getString("name")} op $j",
                    expect.getString(expectedIndex),
                    verdict.name.lowercase(),
                )
                expectedIndex++
            }
            assertEquals(
                "case: ${case.getString("name")} gaps",
                case.optInt("gaps", 0),
                receiver.state.gaps,
            )
        }
    }

    // ------------------------------------------------------------ 逐条断言

    @Test
    fun `解码永不抛异常`() {
        val nasty = listOf(
            byteArrayOf(),
            byteArrayOf(0),
            byteArrayOf(-1, -2),
            ByteArray(5000),
            "{".toByteArray(),
            "}".toByteArray(),
            "[]".toByteArray(),
            "null".toByteArray(),
            """{"v":2}""".toByteArray(),
            """{"v":2,"t":"x"}""".toByteArray(),
            "表情".toByteArray(),
        )
        for (data in nasty) {
            // 只要不抛就算过；具体状态由上面的向量覆盖。
            ProtocolCodec.decode(data)
        }
    }

    @Test
    fun `超大包在解析前就被挡掉`() {
        // 70 KB 的合法 JSON：先解析会成功；正确实现应该直接 TOO_LARGE。
        val big = (
            """{"v":2,"t":"presentation.emotion","id":"i","seq":0,"ep":"e","args":{"x":"""" +
                "a".repeat(71680) + """"}}"""
            ).toByteArray()
        assertTrue(big.size > 65535)
        assertEquals(ProtocolCodec.Status.TOO_LARGE, ProtocolCodec.decode(big).status)
    }

    @Test
    fun `体积边界 4096 收 4097 拒`() {
        fun build(target: Int): ByteArray {
            val obj = JSONObject()
                .put("v", 2)
                .put("t", "presentation.emotion")
                .put("id", "i")
                .put("seq", 0)
                .put("ep", "e")
                .put("args", JSONObject().put("pad", ""))
            val base = obj.toString().toByteArray(Charsets.UTF_8).size
            obj.getJSONObject("args").put("pad", "x".repeat(target - base))
            val data = obj.toString().toByteArray(Charsets.UTF_8)
            assertEquals(target, data.size)
            return data
        }
        assertEquals(ProtocolCodec.Status.OK, ProtocolCodec.decode(build(4096)).status)
        assertEquals(ProtocolCodec.Status.TOO_LARGE, ProtocolCodec.decode(build(4097)).status)
    }

    @Test
    fun `中文不转义地往返`() {
        val envelope = ProtocolCodec.newEnvelope(
            eventType = "presentation.emotion",
            args = JSONObject().put("emotion", "开心"),
            seq = 3,
            ep = "e",
            id = "i",
        )
        val data = ProtocolCodec.encode(envelope)
        assertTrue("中文应该按 UTF-8 走而不是 \\uXXXX", String(data, Charsets.UTF_8).contains("开心"))
        val back = ProtocolCodec.decode(data)
        assertEquals(ProtocolCodec.Status.OK, back.status)
        assertEquals("开心", back.envelope!!.args.getString("emotion"))
    }

    @Test
    fun `编码省略为空的可选字段`() {
        val envelope = ProtocolCodec.newEnvelope("presentation.emotion", JSONObject(), 0, "e", "i")
        val json = JSONObject(String(ProtocolCodec.encode(envelope), Charsets.UTF_8))
        assertFalse(json.has("ts"))
        assertFalse(json.has("at"))
        assertFalse(json.has("dst"))
    }

    @Test
    fun `编码超限时断言失败而不是截断`() {
        val envelope = ProtocolCodec.newEnvelope(
            "presentation.emotion",
            JSONObject().put("x", "a".repeat(5000)),
            0, "e", "i",
        )
        try {
            ProtocolCodec.encode(envelope)
            fail("应该抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("exceeds"))
        }
    }

    @Test
    fun `dst 匹配`() {
        val broadcast = ProtocolCodec.newEnvelope("presentation.emotion", JSONObject(), 0, "e", "i")
        assertTrue(broadcast.isFor("anyone"))

        val targeted = ProtocolCodec.newEnvelope(
            "presentation.emotion", JSONObject(), 0, "e", "i", dst = listOf("u-1"),
        )
        assertTrue(targeted.isFor("u-1"))
        assertFalse(targeted.isFor("u-2"))

        val empty = ProtocolCodec.newEnvelope(
            "presentation.emotion", JSONObject(), 0, "e", "i", dst = emptyList(),
        )
        assertTrue(empty.isFor("anyone"))
    }

    @Test
    fun `seq 是 64 位`() {
        val envelope = ProtocolCodec.newEnvelope(
            "presentation.state", JSONObject(), 9007199254740991L, "e", "i",
        )
        val back = ProtocolCodec.decode(ProtocolCodec.encode(envelope))
        assertEquals(ProtocolCodec.Status.OK, back.status)
        assertEquals(9007199254740991L, back.envelope!!.seq)
    }

    @Test
    fun `布尔值不算版本号`() {
        val data = """{"v":true,"t":"presentation.emotion","id":"i","seq":0,"ep":"e","args":{}}"""
        assertEquals(ProtocolCodec.Status.BAD_VALUE, ProtocolCodec.decode(data.toByteArray()).status)
    }

    // ------------------------------------------------------------ 目录

    @Test
    fun `目录里每条事件的 topic 都是四个之一`() {
        val topics = setOf(
            ProtocolCatalog.TOPIC_CUE, ProtocolCatalog.TOPIC_CTL,
            ProtocolCatalog.TOPIC_IN, ProtocolCatalog.TOPIC_TEL,
        )
        for ((name, spec) in ProtocolCatalog.EVENTS) {
            assertTrue("$name 的 topic ${spec.topic} 不在白名单里", spec.topic in topics)
        }
    }

    @Test
    fun `speech 事件都在保序的 cue 流上`() {
        for (name in listOf("speech.seg", "speech.end")) {
            assertEquals(ProtocolCatalog.TOPIC_CUE, ProtocolCatalog.specOf(name)!!.topic)
        }
    }

    @Test
    fun `投递类与目录一致`() {
        assertEquals(
            ProtocolCatalog.Delivery.DEADLINE,
            ProtocolCatalog.specOf("presentation.emotion")!!.delivery,
        )
        assertEquals(ProtocolCatalog.Delivery.MUST, ProtocolCatalog.specOf("speech.seg")!!.delivery)
        assertEquals(
            ProtocolCatalog.Delivery.DROPPABLE,
            ProtocolCatalog.specOf("telemetry.health")!!.delivery,
        )
    }

    @Test
    fun `maySend 与 allowed 互为镜像`() {
        assertTrue(
            ProtocolCatalog.maySend(
                ProtocolCatalog.TOPIC_CTL, "control.hello", ProtocolCatalog.Side.CLIENT,
            )
        )
        assertFalse(
            ProtocolCatalog.maySend(
                ProtocolCatalog.TOPIC_CTL, "control.hello", ProtocolCatalog.Side.SERVER,
            )
        )
        assertFalse(
            ProtocolCatalog.maySend(
                ProtocolCatalog.TOPIC_CUE, "speech.seg", ProtocolCatalog.Side.CLIENT,
            )
        )
    }

    @Test
    fun `第三方伪造 cue 会被方向白名单挡掉`() {
        // 房间里任何参与者都能发数据包；一条"另一个客户端发来的 presentation.emotion"
        // 必须被拒（F8）。这里表达的是"客户端能不能收"——能收，但发布者校验在接线层，
        // 目录这一层负责的是"这条 t 是否属于这个 topic 且发给我"。
        assertTrue(
            ProtocolCatalog.allowed(
                ProtocolCatalog.TOPIC_CUE, "presentation.emotion", ProtocolCatalog.Side.CLIENT,
            )
        )
        // 反过来：客户端不能往 cmp.cue 发。
        assertFalse(
            ProtocolCatalog.maySend(
                ProtocolCatalog.TOPIC_CUE, "presentation.emotion", ProtocolCatalog.Side.CLIENT,
            )
        )
        // 客户端伪造 control.response 是目录允许的（它确实是客户端发的），
        // 所以"是不是真的用户"只能靠身份白名单，不是目录。
        assertTrue(
            ProtocolCatalog.maySend(
                ProtocolCatalog.TOPIC_CTL, "control.response", ProtocolCatalog.Side.CLIENT,
            )
        )
    }

    // ------------------------------------------------------------ 收侧

    @Test
    fun `去重表有界`() {
        val receiver = ProtocolReceiver()
        for (i in 0 until 300) {
            receiver.observe(
                ProtocolCodec.newEnvelope("presentation.emotion", JSONObject(), i.toLong(), "e", "m$i"),
                "agent", ProtocolCatalog.TOPIC_CUE,
            )
        }
        assertEquals(256, receiver.state.seen["agent"]!!.size)
        assertFalse(receiver.state.seen["agent"]!!.contains("m0"))
    }

    @Test
    fun `重连清表后同 epoch 也能从 0 重来`() {
        val receiver = ProtocolReceiver()
        for (i in 0 until 3) {
            receiver.observe(
                ProtocolCodec.newEnvelope("presentation.emotion", JSONObject(), i.toLong(), "e1", "m$i"),
                "agent", ProtocolCatalog.TOPIC_CUE,
            )
        }
        receiver.forgetAll()
        assertTrue(receiver.state.lastSeq.isEmpty())
        assertTrue(receiver.state.seen.isEmpty())

        // 清完之后同 id 重发会被当成新的（这是对的：重连后不知道对端看到了什么）。
        val verdict = receiver.observe(
            ProtocolCodec.newEnvelope("presentation.emotion", JSONObject(), 0, "e1", "m0"),
            "agent", ProtocolCatalog.TOPIC_CUE,
        )
        assertEquals(ProtocolReceiver.Verdict.ACCEPT, verdict)
    }

    @Test
    fun `计数按结果分开记`() {
        val receiver = ProtocolReceiver()
        val e = ProtocolCodec.newEnvelope("presentation.emotion", JSONObject(), 0, "e", "m0")
        receiver.observe(e, "agent", ProtocolCatalog.TOPIC_CUE)
        receiver.observe(e, "agent", ProtocolCatalog.TOPIC_CUE)   // 重复
        receiver.observe(
            ProtocolCodec.newEnvelope("presentation.emotion", JSONObject(), 5, "e", "m5"),
            "agent", ProtocolCatalog.TOPIC_CUE,
        )
        assertEquals(3, receiver.received)
        assertEquals(2, receiver.applied)
        assertEquals(1, receiver.droppedDup)
        assertEquals(1, receiver.state.gaps)
    }

    @Test
    fun `收侧状态机不看 args`() {
        val receiver = ProtocolReceiver()
        val weird = listOf(
            JSONObject(),
            JSONObject().put("weird", JSONArray().put(1).put(JSONObject.quote("x"))),
            JSONObject().put("x", Double.MAX_VALUE),
        )
        for (args in weird) {
            receiver.forgetAll()
            val e = ProtocolCodec.newEnvelope("presentation.emotion", args, 0, "e", "i")
            assertEquals(
                ProtocolReceiver.Verdict.ACCEPT,
                receiver.observe(e, "a", ProtocolCatalog.TOPIC_CUE),
            )
        }
    }

    @Test
    fun `协议大版本是 2`() {
        assertEquals(2, ProtocolCodec.PROTOCOL_MAJOR)
    }
}
