package com.openapi.feature.chat.data.remote

import com.openapi.feature.chat.domain.model.ChatStreamEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

/**
 * `/v1` 事件帧解析后的「本轮事件」。JSON 只在 data 层解析一次，domain 层不认识帧格式；
 * 折叠逻辑（[TurnEventCollector]）因此能在纯 JVM 测试里跑——测试注入自己的解析，不碰 org.json。
 */
internal sealed interface ParsedTurnEvent {
    /** 帧自带的 turn_id；空串表示这帧没带。 */
    val turnId: String

    /** 已生成的全部文字（订阅/重连时服务端补发，用于对齐断线期间漏掉的尾巴）。 */
    data class Snapshot(override val turnId: String, val text: String) : ParsedTurnEvent
    data class Delta(override val turnId: String, val delta: String) : ParsedTurnEvent
    data class Done(override val turnId: String, val assistantMessageId: Long) : ParsedTurnEvent
    data class Cancelled(override val turnId: String, val assistantMessageId: Long) : ParsedTurnEvent
    data class Errored(override val turnId: String, val code: String) : ParsedTurnEvent
}

/** 把一个 SSE 帧翻译成 [ParsedTurnEvent]；data 不是 JSON 或事件与本轮无关时返回 null。 */
internal fun parseAgentEvent(frame: AgentSseFrame): ParsedTurnEvent? {
    val obj = runCatching { JSONObject(frame.data) }.getOrNull() ?: return null
    val turnId = obj.optString("turn_id", "")
    return when (frame.event) {
        "turn.snapshot" -> ParsedTurnEvent.Snapshot(turnId, obj.optString("text", ""))
        "message.delta" -> ParsedTurnEvent.Delta(turnId, obj.optString("delta", ""))
        "message.done" -> ParsedTurnEvent.Done(turnId, obj.optLong("assistant_message_id", 0L))
        "turn.cancelled" -> ParsedTurnEvent.Cancelled(turnId, obj.optLong("assistant_message_id", 0L))
        "error" -> ParsedTurnEvent.Errored(turnId, obj.optString("code", "unknown"))
        else -> null
    }
}

/** `/v1` 的错误码是本机看不懂的短串，翻成用户能据此行动的话。 */
internal fun describeAgentError(code: String): String = when (code) {
    "content_policy" -> "这条消息不符合内容规范"
    "rate_limited" -> "太频繁了，稍等一下再发"
    "conflict" -> "上一轮回复还没结束"
    "invalid_json" -> "回复格式异常，请重试"
    else -> "回复失败（$code），请重试"
}

/**
 * 把会话事件流折叠成一轮 [ChatStreamEvent]，并在连接中途断掉时自动续传。
 *
 * 为什么要自动续传：服务端把每轮回复写进 Redis Stream，重新订阅会先补发 `turn.snapshot`
 * （已生成的全部文字），`Last-Event-ID` 还能精确断点续传——**断线本来什么都不该丢**。
 * 但以前的实现只订阅一次：移动网络切一下、代理掐掉长连接，用户就收到
 * 「回复没有完整收到，请重试」，而服务端明明还在正常生成、随时能补发。
 * 现在改成：掉线后退避重连（1s、2s、4s），每次重连带上新开流时记住的 Last-Event-ID；
 * snapshot 会重发全文，[collect] 里只把本地缺的尾巴补出来，不会重复出字。
 * 连续掉线 [backoffMs] + 1 次还没等到 done，才退回旧行为：半截按半截收尾、全空报错，
 * 由上层（ReplyStreamer）兜底拉服务端历史对齐。
 *
 * 401 之类怎么重试都不会好的错误（[isFatal]）不重试，立刻报错——与另一条 UI 的
 * AgentEventReconnector 的策略一致。
 */
internal class TurnEventCollector(
    /** 开一条事件流；[lastEventId] 非空时服务端按它断点续传。 */
    private val open: suspend (lastEventId: String?) -> Flow<AgentSseFrame>,
    private val parse: (AgentSseFrame) -> ParsedTurnEvent?,
    /** 退避表：第 n 次掉线后等 backoffMs[n-1] 毫秒再重连；次数 = 表长。 */
    private val backoffMs: List<Long> = listOf(1_000L, 2_000L, 4_000L),
    private val wait: suspend (Long) -> Unit = { delay(it) },
    private val isFatal: (Throwable) -> Boolean = { false },
    private val describe: (String) -> String = ::describeAgentError,
) {

    /** 用 `throw TurnFinished` 退出 collect——flow 的 collect 没有别的提前结束手段。 */
    private object TurnFinished : Exception() {
        override fun fillInStackTrace(): Throwable = this
    }

    /**
     * 收集一整轮。事件通过 [emit] 交给调用方（在调用方的 flow{} 里就是 emit 给下游）；
     * 正常 done 时先 [persist] 完整文字再发 Done，其余收尾（cancelled / 半截 / 报错）不发 persist。
     */
    suspend fun collect(
        emit: suspend (ChatStreamEvent) -> Unit,
        expectedTurnId: String?,
        assistantLocalId: String,
        persist: suspend (fullText: String) -> Unit,
    ) {
        val acc = StringBuilder()
        var matchedTurn: String? = expectedTurnId
        var finished = false
        var errorEmitted = false
        // 新开流时从哪续传：每条帧自带的 id，断线重连时原样带回
        var lastEventId: String? = null
        var drops = 0

        while (!finished) {
            try {
                open(lastEventId).collect { frame ->
                    frame.id?.let { lastEventId = it }
                    val ev = parse(frame) ?: return@collect
                    // 第一次拿到 turn_id 时固定下来，后续只认这一轮，避免并发回合串台。
                    if (ev.turnId.isNotEmpty() && matchedTurn == null) matchedTurn = ev.turnId
                    val ours = ev.turnId.isNotEmpty() && ev.turnId == matchedTurn
                    when (ev) {
                        is ParsedTurnEvent.Snapshot -> if (ours) {
                            // 补发的是「已生成的全部文字」，把本地没有的尾巴补上即可。
                            if (ev.text.length > acc.length && ev.text.startsWith(acc.toString())) {
                                val tail = ev.text.substring(acc.length)
                                acc.append(tail)
                                emit(ChatStreamEvent.Delta(assistantLocalId, tail))
                            }
                        }
                        is ParsedTurnEvent.Delta -> if (ours && ev.delta.isNotEmpty()) {
                            acc.append(ev.delta)
                            emit(ChatStreamEvent.Delta(assistantLocalId, ev.delta))
                        }
                        is ParsedTurnEvent.Done -> if (ours) {
                            persist(acc.toString())
                            emit(
                                ChatStreamEvent.Done(
                                    assistantId = ev.assistantMessageId.takeIf { it > 0 }?.toString(),
                                    userId = null,
                                    fullText = acc.toString(),
                                ),
                            )
                            finished = true
                            throw TurnFinished
                        }
                        is ParsedTurnEvent.Cancelled -> if (ours) {
                            emit(
                                ChatStreamEvent.Done(
                                    assistantId = ev.assistantMessageId.takeIf { it > 0 }?.toString(),
                                    userId = null,
                                    fullText = acc.toString(),
                                ),
                            )
                            finished = true
                            throw TurnFinished
                        }
                        is ParsedTurnEvent.Errored -> if (ours || matchedTurn == null) {
                            emit(ChatStreamEvent.Error(describe(ev.code), null))
                            errorEmitted = true
                            finished = true
                            throw TurnFinished
                        }
                    }
                }
                // 服务端正常关闭了连接但这一轮还没 done：同样按掉线处理，下面重连。
            } catch (_: TurnFinished) {
                // 本轮正常收尾（done / cancelled / error），退出。
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isFatal(e)) {
                    // 登录过期这类错误重连一百次也是同一个结果，直接报给界面。
                    if (!errorEmitted) emit(ChatStreamEvent.Error(e.message ?: "事件流出错"))
                    return
                }
            }
            if (finished) break
            drops++
            if (drops > backoffMs.size) break
            wait(backoffMs[drops - 1])
        }

        if (!finished && !errorEmitted) {
            // 重连次数用完仍没等到 done：把已收到的内容交出去，
            // 让界面保留半截回复而不是整段消失（上层会再兜底拉一次历史）。
            if (acc.isNotEmpty()) {
                persist(acc.toString())
                emit(ChatStreamEvent.Done(assistantId = null, userId = null, fullText = acc.toString()))
            } else {
                emit(ChatStreamEvent.Error("没有收到回复，请重试"))
            }
        }
    }
}
