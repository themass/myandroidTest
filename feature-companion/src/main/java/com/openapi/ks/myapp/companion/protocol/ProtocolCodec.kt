package com.openapi.ks.myapp.companion.protocol

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * 协议 v2 信封的编解码（§5）。
 *
 * 纯字符串进出，不依赖 LiveKit 与协程，所以能在真正接线之前就把「两端是不是同一个
 * 理解」用单测锁住。这里只负责校验、序列化、以及去重/序号的状态机。
 *
 * **为什么把失败分类而不是抛一个异常**：§14.1 要求每一种失败进不同的计数桶，运维
 * 靠这些桶区分「是网络还是我的编译器」。抛 `JSONException` 上层只能知道「坏了」，
 * 说不出坏在哪。
 *
 * 用 `org.json` 而不是 kotlinx.serialization 是刻意的：这个 module 已经在用它，
 * 为一个协议层引入第二个 JSON 库不值得。代价是它宽容（数字可以是 Long 也可以是
 * Double），所以类型判断必须自己写，见 [intOrNull]。
 */
object ProtocolCodec {

    /** 协议大版本。`v` 只增不减；不等于这个值的整条丢弃，不尝试部分理解（§5 规则 1）。 */
    const val PROTOCOL_MAJOR = 2

    /**
     * 单条消息的硬限。SDK 强限 65535 B（§4.3），但 4 KiB 以上一律视为实现缺陷，
     * 由编解码层直接拒收并计数，而不是让它走到网络层。
     */
    const val MAX_PAYLOAD_BYTES = 4096

    /** 自限口径（§4.3）。发送侧断言用，接收侧不看这个值。 */
    const val SOFT_PAYLOAD_BYTES = 12288

    /** 一条消息的解析结果。前六种是失败，分别对应 §14.1 的一个计数桶。 */
    enum class Status {
        OK,
        BAD_JSON,
        MISSING_FIELD,
        BAD_VALUE,
        VERSION,
        TOO_LARGE,

        /**
         * `t` 不认识。它**不算失败**——消息本身是好的，只是这一端还没有这个能力，
         * 忽略即可（§5 规则 2）。这是唯一的向前兼容手段。
         */
        UNKNOWN_TYPE,
    }

    private val REQUIRED = listOf("v", "t", "id", "seq", "ep", "args")

    /**
     * 解开之后的一封信。
     *
     * `rawBytes` 留着是为了埋点：`payload_bytes_hist` 要的是原始体积，不是重新
     * 序列化之后的体积。
     */
    data class Envelope(
        val v: Int,
        val t: String,
        val id: String,
        val seq: Long,
        val ep: String,
        val args: JSONObject,
        val ts: Long? = null,
        val at: JSONObject? = null,
        val dst: List<String>? = null,
        val rawBytes: Int = 0,
    ) {
        /** `dst` 为空/缺省即广播；非空时只有列出的 identity 该处理（§5 规则 6）。 */
        fun isFor(identity: String): Boolean =
            dst.isNullOrEmpty() || identity in dst
    }

    /** 解析结果。`envelope` 只在 [Status.OK] 时非空。 */
    data class Result(val envelope: Envelope?, val status: Status)

    private fun ok(envelope: Envelope) = Result(envelope, Status.OK)

    private fun fail(status: Status) = Result(null, status)

    /**
     * `org.json` 的数字可能是 `Int`、`Long`、`BigInteger` 或 `Double`。`seq` 是
     * 64 位（§5.1），不能按 `Int` 读。`Boolean` 不算数字——`get("v") as? Number`
     * 在 Kotlin 里不会把 `true` 转成 1，但 `JSONObject.opt` 的宽容行为要显式挡掉。
     */
    private fun intOrNull(value: Any?): Int? = when (value) {
        is Int -> value
        is Long -> if (value in Int.MIN_VALUE..Int.MAX_VALUE) value.toInt() else null
        else -> null
    }

    private fun longOrNull(value: Any?): Long? = when (value) {
        is Int -> value.toLong()
        is Long -> value
        is Double -> if (value == Math.floor(value) && !value.isInfinite()) value.toLong() else null
        else -> null
    }

    private fun stringOrNull(value: Any?): String? = value as? String

    /**
     * 解一封信。
     *
     * 先看体积再解析：70 KB 的恶意包不该让 JSON 解析器先跑一遍（F9）。
     */
    fun decode(data: ByteArray): Result {
        if (data.size > MAX_PAYLOAD_BYTES) return fail(Status.TOO_LARGE)

        val text = try {
            String(data, Charsets.UTF_8).trim()
        } catch (e: Exception) {
            return fail(Status.BAD_JSON)
        }
        if (text.isEmpty()) return fail(Status.BAD_JSON)

        val obj = try {
            JSONObject(text)
        } catch (e: JSONException) {
            return fail(Status.BAD_JSON)
        }

        for (key in REQUIRED) {
            if (!obj.has(key)) return fail(Status.MISSING_FIELD)
        }

        val v = intOrNull(obj.opt("v")) ?: return fail(Status.BAD_VALUE)
        // `v > 2` 是未来版本，整条忽略不尝试部分理解（规则 1）；`v < 2` 不可能出现在
        // cmp.* 上——旧客户端根本不发 cmp.*。
        if (v != PROTOCOL_MAJOR) return fail(Status.VERSION)

        val t = stringOrNull(obj.opt("t"))?.takeIf { it.isNotEmpty() }
            ?: return fail(Status.BAD_VALUE)
        val id = stringOrNull(obj.opt("id"))?.takeIf { it.isNotEmpty() }
            ?: return fail(Status.BAD_VALUE)
        val seq = longOrNull(obj.opt("seq"))?.takeIf { it >= 0 }
            ?: return fail(Status.BAD_VALUE)
        val ep = stringOrNull(obj.opt("ep"))?.takeIf { it.isNotEmpty() }
            ?: return fail(Status.BAD_VALUE)
        val args = obj.opt("args") as? JSONObject ?: return fail(Status.BAD_VALUE)

        val ts = if (obj.has("ts") && !obj.isNull("ts")) {
            longOrNull(obj.opt("ts")) ?: return fail(Status.BAD_VALUE)
        } else null

        val at = if (obj.has("at") && !obj.isNull("at")) {
            obj.opt("at") as? JSONObject ?: return fail(Status.BAD_VALUE)
        } else null

        val dst: List<String>? = if (obj.has("dst") && !obj.isNull("dst")) {
            val arr = obj.opt("dst") as? JSONArray ?: return fail(Status.BAD_VALUE)
            val items = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                val item = arr.opt(i) as? String ?: return fail(Status.BAD_VALUE)
                items.add(item)
            }
            items
        } else null

        // 「t 不认识」是这一层就能判断的失败，早点返回比让调用方再查一次表更省事。
        if (ProtocolCatalog.specOf(t) == null) return fail(Status.UNKNOWN_TYPE)

        return ok(
            Envelope(
                v = v, t = t, id = id, seq = seq, ep = ep, args = args,
                ts = ts, at = at, dst = dst, rawBytes = data.size,
            )
        )
    }

    /**
     * 序列化。`ts` / `at` / `dst` 为 null 时不写进 JSON——少几个字节，也少一处歧义。
     *
     * 超过 [MAX_PAYLOAD_BYTES] 直接抛 `IllegalArgumentException`（发送侧断言失败，
     * §14.1）：能编出来就说明上游在拼一条不该存在的消息，悄悄截断或分批会让接收方
     * 收到半条指令。
     */
    fun encode(envelope: Envelope): ByteArray {
        val obj = JSONObject()
            .put("v", envelope.v)
            .put("t", envelope.t)
            .put("id", envelope.id)
            .put("seq", envelope.seq)
            .put("ep", envelope.ep)
            .put("args", envelope.args)
        envelope.ts?.let { obj.put("ts", it) }
        envelope.at?.let { obj.put("at", it) }
        envelope.dst?.let { obj.put("dst", JSONArray(it)) }

        val data = obj.toString().toByteArray(Charsets.UTF_8)
        require(data.size <= MAX_PAYLOAD_BYTES) {
            "payload ${data.size} B exceeds $MAX_PAYLOAD_BYTES B"
        }
        return data
    }

    /**
     * 组一封新信。`id` 由发送方给，不在这里生成——重发时 id 必须不变（§5 规则 4）。
     */
    fun newEnvelope(
        eventType: String,
        args: JSONObject,
        seq: Long,
        ep: String,
        id: String,
        ts: Long? = null,
        at: JSONObject? = null,
        dst: List<String>? = null,
    ): Envelope = Envelope(
        v = PROTOCOL_MAJOR, t = eventType, id = id, seq = seq, ep = ep, args = args,
        ts = ts, at = at, dst = dst,
    )
}
