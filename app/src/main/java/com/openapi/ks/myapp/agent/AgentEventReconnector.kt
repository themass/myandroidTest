package com.openapi.ks.myapp.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 断线重连（客户端设计 4.1）。
 *
 * - 带 Last-Event-ID 续传；
 * - 指数退避 1s、2s、4s…，上限 30s；成功收到帧后清零；
 * - 连续失败超过 [maxFailures] 次进入 OFFLINE，由用户手动重试（重新收集即可）；
 * - 服务端 `reconnect` 不计入失败，按它给的 afterMs 等待后重连；
 * - 服务端 `gone` 表示续传窗口已过期：发出 Gone 后结束，由调用方重拉历史。
 *
 * **帧间看门狗**（A39，[frameGapMs]）：与主线 `TurnEventCollector` 同一判据——
 * 「连接活着、服务端照常发 15 秒心跳注释、模型不再出字」时，OkHttp 的 60 秒 readTimeout
 * 永远不会触发（心跳让 TCP 有字节），旧实现里这条流既不超时也不抛异常：回合气泡
 * `active` 只有 done/cancelled/failed 才清除，于是「正在生成…」永久挂着，
 * 用户看到的就是「agent 一直不结束、一直在生成」。看门狗只在**回合进行中**武装
 * （协议自己跟踪：snapshot/started 起、done/cancelled/failed 落）——空闲会话的
 * 静默是正常的，不该掐线。触发后按一次失败走退避重连；重连耗尽进 OFFLINE，
 * 界面层（`AgentChatState.onStreamOffline`）把半截回合收尾并给出提示。
 */
class AgentEventReconnector(
    /** 开一条事件流（收集即连接，取消即断开）。拆开注入是为了可测（无需真 [AgentEventSource]）。 */
    private val open: suspend (sessionId: String, lastEventId: String?) -> Flow<SseFrame>,
    private val maxFailures: Int = 5,
    private val waitFn: suspend (Long) -> Unit = { delay(it) },
    /**
     * 回合进行中的最大静默：服务端心跳 15 秒一次，连续三个周期没有任何**帧**
     * （心跳注释不是帧）即判这条流对回合已死。0 或负数 = 关闭看门狗。
     */
    private val frameGapMs: Long = DEFAULT_FRAME_GAP_MS,
) {

    /** 兼容旧调用点的便捷构造。 */
    constructor(source: AgentEventSource, maxFailures: Int = 5) :
        this(source::open, maxFailures)

    fun updates(sessionId: String, initialLastEventId: String?): Flow<StreamUpdate> = flow {
        var lastId = initialLastEventId
        var failures = 0
        var backoff = INITIAL_BACKOFF_MS
        var connected = false
        // 协议层的回合真相：只由事件流本身跟踪，不依赖界面状态。
        var turnActive = false

        while (true) {
            try {
                coroutineScope {
                    val frames = Channel<SseFrame>(Channel.BUFFERED)
                    // 上游掉线原因留一份：通道带 cause 关闭后原样抛出，走既有的重连分支
                    //（不带 cause 会把 401/连接重置误判成普通断流——主线实测教训）。
                    val upstream = arrayOfNulls<Throwable>(1)
                    val pump = launch {
                        try {
                            open(sessionId, lastId).collect { frame -> frames.send(frame) }
                        } catch (c: CancellationException) {
                            throw c
                        } catch (t: Throwable) {
                            upstream[0] = t
                        } finally {
                            frames.close(upstream[0])
                        }
                    }
                    try {
                        while (true) {
                            val received = if (frameGapMs > 0) {
                                withTimeoutOrNull(frameGapMs) { frames.receiveCatching() }
                            } else {
                                frames.receiveCatching()
                            }
                            if (received == null) {
                                // 静默超时：空闲会话正常（心跳不产生帧），继续等；
                                // 回合进行中则判这条流对回合已死，走失败退避重连。
                                if (turnActive) throw TurnStall
                                continue
                            }
                            received.exceptionOrNull()?.let { throw it }
                            // 无 cause 关闭 = 服务端正常收尾：按断线处理，退避重连。
                            if (received.isClosed) break
                            val frame = received.getOrThrow()
                            frame.id?.let { lastId = it }
                            if (!connected) {
                                connected = true
                                failures = 0
                                backoff = INITIAL_BACKOFF_MS
                                emit(StreamUpdate.Connection(ConnectionState.CONNECTED))
                            }
                            when (val event = frame.toSessionEvent() ?: continue) {
                                is SessionEvent.Reconnect -> throw ReconnectRequested(event.afterMs)
                                is SessionEvent.Gone -> {
                                    emit(StreamUpdate.Event(event))
                                    throw GoneStop
                                }
                                is SessionEvent.TurnSnapshot, is SessionEvent.TurnStarted -> {
                                    turnActive = true
                                    emit(StreamUpdate.Event(event))
                                }
                                is SessionEvent.Done, is SessionEvent.TurnCancelled, is SessionEvent.Failed -> {
                                    turnActive = false
                                    emit(StreamUpdate.Event(event))
                                }
                                else -> emit(StreamUpdate.Event(event))
                            }
                        }
                    } finally {
                        // 本轮连接定局（异常/正常收尾都要走）：断开 pump 持有的 HTTP 流。
                        pump.cancel()
                    }
                }
                // 服务端正常关闭连接：按断线处理，退避后立即重连。
                connected = false
            } catch (stop: GoneStop) {
                return@flow
            } catch (requested: ReconnectRequested) {
                connected = false
                emit(StreamUpdate.Connection(ConnectionState.RECONNECTING))
                waitFn(requested.afterMs)
                continue
            } catch (e: CancellationException) {
                throw e
            } catch (e: AgentHttpException) {
                if (e.statusCode == 401) {
                    emit(StreamUpdate.Connection(ConnectionState.OFFLINE))
                    return@flow
                }
                connected = false
                failures++
            } catch (e: Exception) {
                // 含 TurnStall：看门狗判死的回合按一次失败走同一条退避重连路径。
                connected = false
                failures++
            }

            if (failures >= maxFailures) {
                // 到这里回合可能还挂着（流死了但 done 没来过）：界面层在收到 OFFLINE 时
                // 把半截 `active` 收尾（AgentChatState.onStreamOffline），绝不永挂。
                emit(StreamUpdate.Connection(ConnectionState.OFFLINE))
                return@flow
            }
            emit(StreamUpdate.Connection(ConnectionState.RECONNECTING))
            waitFn(backoff)
            backoff = minOf(backoff * 2, MAX_BACKOFF_MS)
        }
    }

    private class ReconnectRequested(val afterMs: Long) : Exception()

    private object GoneStop : Exception()

    /** 看门狗：回合进行中、连续 [frameGapMs] 没有任何帧。按一次连接失败处理。 */
    private object TurnStall : Exception() {
        override fun fillInStackTrace(): Throwable = this
    }

    companion object {
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L

        /** 与主线 [com.openapi.feature.chat.data.remote] 看门狗同值：3 个心跳周期。 */
        const val DEFAULT_FRAME_GAP_MS = 45_000L
    }
}
