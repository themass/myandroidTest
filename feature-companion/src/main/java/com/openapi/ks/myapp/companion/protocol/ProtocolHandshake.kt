package com.openapi.ks.myapp.companion.protocol

import org.json.JSONObject

/**
 * 握手与能力协商（§7 / §12.1）。
 *
 * 与服务端 `handshake.py` 是同一套规则。客户端这一侧多做一件事：把自己认识的
 * 能力表报上去（[CLIENT_CAPS]），并从中算出「我能不能收某个 `t`」。
 *
 * 三条规则值得重复：
 *
 * * **交集取低版本**：`hello_ack.caps` 每一项取两边较小的版本号；**值为 0 的项要
 *   被当作「知道但未启用」保留**，删掉它等于告诉对方这个能力根本不存在。
 * * **3 秒兼容窗口**：老客户端从不发 hello，所以握手是客户端主动发起的；我们现在
 *   是 v2 客户端，必须发。
 * * **重发不是错误**：重连后要再发一次 hello，而不是「已经握过手就不再理」。
 */
object ProtocolHandshake {

    /** 收不到 hello_ack 时的等待上限。与服务端 §7.3 的 3 秒窗口对称。 */
    const val ACK_TIMEOUT_MS = 3_000L

    /**
     * 客户端认识的每一项能力及其自身版本号。0 表示「知道但未启用」。
     * 与服务端 `SERVER_CAPS` 必须覆盖同一批名字，否则交集会少一项。
     */
    val CLIENT_CAPS: Map<String, Int> = linkedMapOf(
        "presentation.state" to 1,
        "presentation.emotion" to 1,
        "presentation.gesture" to 1,
        "presentation.gaze" to 1,
        "speech.timing" to 1,
        "speech.extend" to 1,
        "perception.attention" to 1,
    )

    /** 客户端自报的上限。服务端据此裁剪它的发送节奏。 */
    val CLIENT_LIMITS: Map<String, Int> = linkedMapOf(
        // 客户端比服务端更保守：现在只认 4 KiB 的硬限（§4.3），等编译器稳定再抬。
        "max_payload_bytes" to ProtocolCodec.MAX_PAYLOAD_BYTES,
        "max_cue_per_sec" to 8,
        "clock_skew_ms" to 0,
    )

    /** 客户端身份，进 hello 的 `client` 字段，只用于诊断。 */
    data class ClientInfo(
        val app: String = "",
        val version: String = "",
        val sdk: String = "",
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("app", app)
            .put("version", version)
            .put("sdk", sdk)
    }

    /** 服务端回来的 hello_ack。 */
    data class HelloAck(
        val sid: String,
        val caps: Map<String, Int>,
        val limits: Map<String, Int>,
        val compat: String,
    )

    /**
     * 组 `control.hello` 的 `args`（§7.2）。
     *
     * `proto` 报的是**区间**，不是单一版本：这样服务端在只支持 v2 的前提下也能明确
     * 知道我们两个都能谈。
     */
    fun helloArgs(
        client: ClientInfo = ClientInfo(),
        protoMin: Int = ProtocolCodec.PROTOCOL_MAJOR,
        protoMax: Int = ProtocolCodec.PROTOCOL_MAJOR,
        caps: Map<String, Int> = CLIENT_CAPS,
        limits: Map<String, Int> = CLIENT_LIMITS,
    ): JSONObject {
        val proto = JSONObject().put("min", protoMin).put("max", protoMax)
        val capsJson = JSONObject()
        caps.forEach { (name, version) -> capsJson.put(name, version) }
        val limitsJson = JSONObject()
        limits.forEach { (name, value) -> limitsJson.put(name, value) }
        return JSONObject()
            .put("proto", proto)
            .put("caps", capsJson)
            .put("limits", limitsJson)
            .put("client", client.toJson())
    }

    /**
     * 解析 `control.hello_ack` 的 `args`。
     *
     * 返回 null 表示这份 ack 不可用（缺字段或类型不对）。调用方应把这条连接当失败
     * 处理，而不是套一个默认能力集——猜出来的能力集会导致发对方收不了的消息。
     */
    fun parseAck(args: JSONObject): HelloAck? {
        val sid = args.optString("sid").takeIf { it.isNotEmpty() } ?: return null
        val compat = args.optString("compat").takeIf { it.isNotEmpty() } ?: return null

        val capsJson = args.opt("caps") as? JSONObject ?: return null
        val caps = linkedMapOf<String, Int>()
        for (name in capsJson.keys()) {
            val version = capsJson.opt(name) as? Int ?: return null
            if (version < 0) return null
            caps[name] = version
        }

        val limits = linkedMapOf<String, Int>()
        (args.opt("limits") as? JSONObject)?.let { json ->
            for (name in json.keys()) {
                (json.opt(name) as? Int)?.let { limits[name] = it }
            }
        }

        return HelloAck(sid = sid, caps = caps, limits = limits, compat = compat)
    }

    /**
     * 服务端能不能往这条连接发某个 `t`——也就是**我们能不能收**。
     *
     * 值为 0 的能力视为未启用。`compat != "v2"` 时一律收不到 cmp.*（服务端只会走
     * 旧的 `companion.*` topic）。
     */
    fun supports(ack: HelloAck, eventType: String): Boolean {
        if (ack.compat != "v2") return false
        return (ack.caps[eventType] ?: 0) > 0
    }

    /**
     * 协商之后的实际口径。
     *
     * 服务端已经把交集算好了，客户端只需要读；这里单独留一个类型是为了让「这一端
     * 到底能收什么」有一个可以传下去的对象，而不是到处传 `HelloAck`。
     */
    data class Negotiator(val ack: HelloAck?) {
        val isV2: Boolean get() = ack?.compat == "v2"

        fun canReceive(eventType: String): Boolean =
            ack != null && supports(ack, eventType)

        /** 服务端允许的最大包体积。没协商出来时退回硬限。 */
        val maxPayloadBytes: Int
            get() = ack?.limits?.get("max_payload_bytes") ?: ProtocolCodec.MAX_PAYLOAD_BYTES
    }
}
