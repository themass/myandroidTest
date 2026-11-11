package com.openapi.ks.myapp.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 断线重连（客户端设计 4.1）。
 *
 * - 带 Last-Event-ID 续传；
 * - 指数退避 1s、2s、4s…，上限 30s；成功收到帧后清零；
 * - 连续失败超过 [maxFailures] 次进入 OFFLINE，由用户手动重试（重新收集即可）；
 * - 服务端 `reconnect` 不计入失败，按它给的 afterMs 等待后重连；
 * - 服务端 `gone` 表示续传窗口已过期：发出 Gone 后结束，由调用方重拉历史。
 */
class AgentEventReconnector(
    private val source: AgentEventSource,
    private val maxFailures: Int = 5,
    private val waitFn: suspend (Long) -> Unit = { delay(it) },
) {
    fun updates(sessionId: String, initialLastEventId: String?): Flow<StreamUpdate> = flow {
        var lastId = initialLastEventId
        var failures = 0
        var backoff = INITIAL_BACKOFF_MS
        var connected = false

        while (true) {
            try {
                source.open(sessionId, lastId).collect { frame ->
                    frame.id?.let { lastId = it }
                    if (!connected) {
                        connected = true
                        failures = 0
                        backoff = INITIAL_BACKOFF_MS
                        emit(StreamUpdate.Connection(ConnectionState.CONNECTED))
                    }
                    when (val event = frame.toSessionEvent() ?: return@collect) {
                        is SessionEvent.Reconnect -> throw ReconnectRequested(event.afterMs)
                        is SessionEvent.Gone -> {
                            emit(StreamUpdate.Event(event))
                            throw GoneStop
                        }
                        else -> emit(StreamUpdate.Event(event))
                    }
                }
                // 服务端正常关闭连接：按断线处理，立即重连。
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
                connected = false
                failures++
            }

            if (failures >= maxFailures) {
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

    private companion object {
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
    }
}
