package com.openapi.feature.chat.data.remote

import com.openapi.feature.chat.domain.model.ChatStreamEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    // 帧间看门狗（本机判的，不是服务端给的码）：这句只是兜底，用户真正看到的这句话
    // 由 presentation 层按系统语言取 ChatStrings.turnTimeout()。
    ChatStreamEvent.Error.CODE_TURN_TIMEOUT -> "等不到新的内容，回复可能已经中断"
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
 *
 * **帧间看门狗**（[frameTimeoutMs]）补上重连完全覆盖不到的另一种死法：连接还在、
 * 服务端还在心跳，但模型不再出字。见 [frameTimeoutMs] 的注释。
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
    /**
     * 帧间看门狗阈值：本轮开始（或收到上一帧）之后，超过这个毫秒数**没有任何 SSE 帧**，
     * 就判定本轮已死，按失败收尾（[ChatStreamEvent.Error]，
     * code = [ChatStreamEvent.Error.CODE_TURN_TIMEOUT]）。0 或负数 = 关闭看门狗。
     *
     * 为什么必须有：事件流的 `readTimeout` 是 0（回复多久吐完完全看模型，见
     * [AgentChatRemote.streamClient] 的注释），而服务端每 15 秒发一次 SSE 心跳注释——
     * `:` 注释行在解析阶段就被丢掉、**根本不产生帧**。于是「连接活着、心跳照常、模型一个字
     * 不出」这种形状既不触发超时也不抛异常：[collect] 永远挂在 collect 里，重连（只由异常
     * 触发）也不会触发，界面上那条「正在思考…」就是永久的，`sending` 一直为真 →
     * 用户怎么发都发不动。「那边失败了这边无法再发新消息」的第一根因就是这个。
     *
     * **可测的判据语义**：「距离上一帧静默了 [frameTimeoutMs]」（每收到一帧重新计时，
     * 连 [parse] 返回 null 的帧也算收到）。因为心跳不产生帧，这一条同时覆盖了
     * 「连心跳都没有」和「有心跳但连续 3 个心跳周期无内容帧」两种情况——前者按无帧同样
     * 超时，后者根本不会有帧进来。反向的保护：正常长回复持续出字，每帧都重新计时，
     * 不会被误伤（见 TurnWatchdogTest 的第二条用例）。
     */
    private val frameTimeoutMs: Long = DEFAULT_FRAME_TIMEOUT_MS,
) {

    private companion object {
        /**
         * 服务端每 15 秒一次心跳；连续三个心跳周期（45 秒）都没有任何回合帧，
         * 就不可能是「模型想得久一点」——判本轮死掉。
         */
        const val DEFAULT_FRAME_TIMEOUT_MS = 45_000L
    }

    /** 看门狗触发（[frameTimeoutMs] 内没有任何帧）。走错误收尾，不重连。 */
    private object FrameTimeout : Exception() {
        override fun fillInStackTrace(): Throwable = this
    }

    /**
     * 上游正常收尾（流结束、无异常）却没等到 done/cancelled/error。
     * 不是看门狗也不是致命错误：按掉线走退避重连，与委托前「for 循环自然结束」语义一致。
     */
    private object StreamEnded : Exception() {
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
        /**
         * 服务端正式用户消息 id（submit 回执里的 message_id；重新生成没有新消息时为 null）。
         * 带进 Done.userId，界面立刻能用它编辑/删除刚发出的用户气泡。
         */
        userMessageId: String?,
        persist: suspend (fullText: String) -> Unit,
    ) {
        val acc = StringBuilder()
        var matchedTurn: String? = expectedTurnId
        var finished = false
        var errorEmitted = false
        // 新开流时从哪续传：每条帧自带的 id，断点重连时原样带回
        var lastEventId: String? = null
        var drops = 0

        while (!finished) {
            try {
                // 帧的**处理与 emit 必须留在当前协程**（调用方是 flow{}，跨协程 emit 会
                // IllegalStateException），所以看门狗不能包在处理侧，只能包在「取下一帧」上：
                // pump 协程只把上游帧送进通道，主循环对 receiveCatching 做可重置的超时。
                coroutineScope {
                    val frames = Channel<AgentSseFrame>(Channel.BUFFERED)
                    // 上游异常（掉线原因）留一份：通道关闭后原样抛出，走既有的 isFatal / 退避重连。
                    // 数组是为了让局部变量在 pump 协程里可写；只在 pump 写、主循环读，
                    // 顺序由「close 之前一定先记完」保证。
                    val upstream = arrayOfNulls<Throwable>(1)
                    val pump = launch {
                        try {
                            open(lastEventId).collect { frame -> frames.send(frame) }
                        } catch (c: CancellationException) {
                            throw c
                        } catch (t: Throwable) {
                            upstream[0] = t
                        } finally {
                            // 必须带 cause 关闭：不带 cause 时通道结果的 exceptionOrNull()
                            // 是 null、getOrThrow() 抛内置 ISE（closed without a cause），
                            // 上游真正的掉线原因（401 / 连接重置）就送不到下面的 isFatal /
                            // 退避分支——致命错误会被当成普通掉线重试（实测回归）。
                            frames.close(upstream[0])
                        }
                    }
                    try {
                        while (!finished) {
                            // 每取一帧重新计时：阈值 = 「距离上一帧静默了多久」，与连接是否活着无关
                            val received = if (frameTimeoutMs > 0) {
                                withTimeoutOrNull(frameTimeoutMs) { frames.receiveCatching() }
                            } else {
                                // 看门狗关闭：一直等（语义与委托前逐字一致）
                                frames.receiveCatching()
                            }
                            if (received == null) throw FrameTimeout
                            received.exceptionOrNull()?.let { closed ->
                                if (closed is CancellationException) throw closed
                                // 通道关闭：上游异常已由 close(cause) 带在这里，原样抛出，
                                // 走既有的 isFatal / 退避重连。
                                throw closed
                            }
                            // 到这里通道若已关闭就是「无 cause 关闭」= 上游正常收尾却没 done：
                            // 造一个掉线哨兵，下面按普通断流退避重连（不能 getOrThrow，
                            // 那抛的是 Channel 内部的 ISE）。
                            if (received.isClosed) throw StreamEnded
                            val frame = received.getOrThrow()
                            frame.id?.let { lastEventId = it }
                            val ev = parse(frame) ?: continue
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
                                            userId = userMessageId,
                                            fullText = acc.toString(),
                                        ),
                                    )
                                    finished = true
                                }
                                is ParsedTurnEvent.Cancelled -> if (ours) {
                                    emit(
                                        ChatStreamEvent.Done(
                                            assistantId = ev.assistantMessageId.takeIf { it > 0 }?.toString(),
                                            userId = userMessageId,
                                            fullText = acc.toString(),
                                        ),
                                    )
                                    finished = true
                                }
                                is ParsedTurnEvent.Errored -> if (ours || matchedTurn == null) {
                                    emit(ChatStreamEvent.Error(describe(ev.code), null))
                                    errorEmitted = true
                                    finished = true
                                }
                            }
                        }
                    } finally {
                        // 本轮已定（done / cancelled / error / 看门狗 / 上游异常）：断开这条连接
                        pump.cancel()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e === FrameTimeout) {
                    // 看门狗触发：模型不再出字。重连在这里没有意义——服务端认为这一轮还活着，
                    // 重订阅只会补发同一条 snapshot。按失败收尾，让 `sending` 落地、
                    // 用户可以重新发；界面上已显示的半截文字由 ReplyStreamer 保留。
                    emit(
                        ChatStreamEvent.Error(
                            describe(ChatStreamEvent.Error.CODE_TURN_TIMEOUT),
                            code = ChatStreamEvent.Error.CODE_TURN_TIMEOUT,
                        ),
                    )
                    errorEmitted = true
                    finished = true
                    continue
                }
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
                // userId 在这里保持 null：上层靠「done 里两个 id 都没有」识别
                // 「没等到 done 帧、半截收尾」并提示「回复没有完整收到」。
                emit(ChatStreamEvent.Done(assistantId = null, userId = null, fullText = acc.toString()))
            } else {
                emit(ChatStreamEvent.Error("没有收到回复，请重试"))
            }
        }
    }
}
