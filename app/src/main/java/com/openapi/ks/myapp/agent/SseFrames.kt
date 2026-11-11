package com.openapi.ks.myapp.agent

import org.json.JSONArray
import org.json.JSONObject

/** 一帧 SSE。 */
data class SseFrame(val id: String?, val event: String, val data: String)

/**
 * SSE 按行解析（W3C 规范的子集）：忽略 `:` 注释帧（心跳），多行 data 用换行拼接，
 * 空行分帧。纯逻辑，不依赖网络，便于单测。
 */
class SseFrameParser {
    private var id: String? = null
    private var event: String = "message"
    private val data = StringBuilder()
    private var hasData = false

    /** 喂入一行（不含换行符）。空行时返回完整的一帧，否则返回 null。 */
    fun feed(line: String): SseFrame? {
        if (line.isEmpty()) {
            if (!hasData) {
                event = "message"
                id = null
                return null
            }
            val frame = SseFrame(id, event, data.toString().removeSuffix("\n"))
            id = null
            event = "message"
            data.setLength(0)
            hasData = false
            return frame
        }
        if (line.startsWith(":")) return null
        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        var value = if (colon < 0) "" else line.substring(colon + 1)
        if (value.startsWith(" ")) value = value.substring(1)
        when (field) {
            "id" -> id = value
            "event" -> event = value
            "data" -> {
                data.append(value).append('\n')
                hasData = true
            }
        }
        return null
    }
}

/**
 * 把一帧映射成 [SessionEvent]。认不出的事件或字段缺失返回 null（D §7.4：客户端忽略未知内容）。
 */
fun SseFrame.toSessionEvent(): SessionEvent? {
    val eid = id ?: return null
    val json = runCatching { JSONObject(data) }.getOrNull()
    return when (event) {
        "message.created" -> json?.let {
            SessionEvent.MessageCreated(eid, it.str("message_id") ?: return null,
                it.optNullableStr("client_message_id"), it.str("role") ?: "user", it.str("content") ?: "",
                it.str("kind") ?: "chat")
        }
        "turn.snapshot" -> json?.let {
            SessionEvent.TurnSnapshot(eid, it.str("turn_id") ?: return null, it.ids("user_message_ids"), it.str("text") ?: "")
        }
        "turn.started" -> json?.let {
            SessionEvent.TurnStarted(eid, it.str("turn_id") ?: return null, it.ids("user_message_ids"))
        }
        "turn.restarted" -> json?.let {
            SessionEvent.TurnRestarted(eid, it.str("turn_id") ?: return null, it.ids("user_message_ids"))
        }
        "message.delta" -> json?.let {
            SessionEvent.Delta(eid, it.str("turn_id") ?: return null, it.str("delta") ?: "")
        }
        "step" -> json?.let {
            SessionEvent.Step(eid, it.str("turn_id") ?: return null,
                AgentStep(it.str("kind") ?: "", it.str("title") ?: "", it.str("detail") ?: ""))
        }
        "input.merged" -> json?.let {
            SessionEvent.InputMerged(eid, it.str("message_id") ?: return null, it.str("turn_id") ?: return null)
        }
        "input.steered" -> json?.let {
            SessionEvent.InputSteered(eid, it.str("message_id") ?: return null, it.str("turn_id") ?: return null)
        }
        "input.queued" -> json?.let {
            SessionEvent.InputQueued(eid, it.str("message_id") ?: return null, it.optIntOrNull("queue_position"))
        }
        "input.withdrawn" -> json?.let { SessionEvent.InputWithdrawn(eid, it.str("message_id") ?: return null) }
        "queue.drained" -> json?.let {
            SessionEvent.QueueDrained(eid, it.str("turn_id") ?: return null, it.ids("message_ids"))
        }
        "message.done" -> json?.let {
            SessionEvent.Done(eid, it.str("turn_id") ?: return null, it.optNullableStr("assistant_message_id"),
                it.ids("steered_message_ids"))
        }
        "turn.cancelled" -> json?.let {
            SessionEvent.TurnCancelled(eid, it.str("turn_id") ?: return null, it.optNullableStr("assistant_message_id"))
        }
        "error" -> json?.let {
            SessionEvent.Failed(eid, it.optNullableStr("turn_id"), it.str("code") ?: "unknown",
                it.optBoolean("retryable", false))
        }
        "action.confirm_required" -> json?.let {
            SessionEvent.ConfirmRequired(eid, it.str("confirmation_id") ?: return null,
                it.str("title") ?: "", it.str("summary") ?: "")
        }
        "action.done" -> json?.let {
            SessionEvent.ActionDone(eid, it.str("confirmation_id") ?: return null, it.str("status") ?: "")
        }
        "reconnect" -> SessionEvent.Reconnect(eid, json?.optLong("after_ms", 1000L) ?: 1000L)
        "gone" -> SessionEvent.Gone(eid)
        else -> null
    }
}

private fun JSONObject.str(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null

private fun JSONObject.optNullableStr(key: String): String? = str(key)

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

private fun JSONObject.ids(key: String): List<String> {
    val array: JSONArray = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).map { array.optString(it) }.filter { it.isNotEmpty() }
}
