package com.openapi.ks.myapp.companion

import kotlinx.coroutines.CancellationException

/**
 * 把 LiveKit「要么返回 [Result]、要么直接抛」的发送 API 归一成单个 [Result]。
 *
 * 真机事故（2026-11-22）：`LocalParticipant.sendText` 在 data channel 未建立时不走
 * `Result.failure`，而是抛 `RoomException$ConnectException`（"channel not established
 * for RELIABLE"）。链在返回值上的 `.onFailure{}` 完全轮不到，异常从 `scope.launch`
 * 协程裸奔成未捕获异常，CustomActivityOnCrash 把整个 app 拉起重启——通话页每点一次
 * 形象就闪退一次。`publishData` 同理。
 *
 * 所有从协程里向房间发数据的调用都必须过这里；`.onFailure` 只对返回值负责，
 * 对抛出的异常它一无所知。
 *
 * [CancellationException] 原样上抛（它是 Exception 的子类，不先排除就会被吞掉，
 * 调用方协程的取消语义随之失效）。
 */
internal inline fun <T> sendGuard(block: () -> Result<T>): Result<T> =
    try {
        block()
    } catch (c: CancellationException) {
        throw c
    } catch (t: Exception) {
        Result.failure(t)
    }
