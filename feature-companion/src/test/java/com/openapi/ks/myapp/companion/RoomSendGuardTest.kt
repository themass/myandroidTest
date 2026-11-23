package com.openapi.ks.myapp.companion

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * [sendGuard] 的回归测试。
 *
 * 真机事故（2026-11-22，demo #2b 验收）：通话页点她头部的上报协程
 * `LiveKitCompanionTransport.sendTouch` v1 回退里 `sendText(...).onFailure{}` **没能**
 * 兜住失败——LiveKit 2.x 在 data channel 未建立时不返回 `Result.failure`，而是直接抛
 * `RoomException$ConnectException("channel not established for RELIABLE")`。异常穿过
 * `scope.launch` 成为未捕获异常，CustomActivityOnCrash 拉起重启页，整个 app 自杀。
 * 触摸点一下炸一次，两条复现路径（pid 9400/9404）日志完全一致。
 */
class RoomSendGuardTest {

    @Test
    fun `抛出的异常被折叠成 Result failure`() {
        val boom = IllegalStateException("channel not established for RELIABLE")
        val outcome = sendGuard<Result<String>> { throw boom }
        assertEquals(boom, outcome.exceptionOrNull())
    }

    @Test
    fun `返回的 Result failure 原样透传`() {
        val boom = IllegalStateException("returned failure")
        val outcome = sendGuard { Result.failure<String>(boom) }
        assertEquals(boom, outcome.exceptionOrNull())
    }

    @Test
    fun `成功路径原样透传`() {
        val outcome = sendGuard { Result.success("msg-1") }
        assertEquals("msg-1", outcome.getOrNull())
    }

    @Test
    fun `取消异常必须继续上抛而不是被吞掉`() {
        // 吞掉 CancellationException 会让调用方协程的取消语义失效（挂断清理变僵尸协程）。
        try {
            sendGuard<Result<String>> { throw CancellationException("scope cancelled") }
            fail("CancellationException 应原样上抛")
        } catch (c: CancellationException) {
            assertTrue(c.message!!.contains("cancelled"))
        }
    }
}
