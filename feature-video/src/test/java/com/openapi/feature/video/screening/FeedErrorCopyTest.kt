package com.openapi.feature.video.screening

import com.openapi.common.net.http.NetError
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 取数失败文案：给用户的人话，不是调试输出。
 *
 * 三代教训：① 拼异常 toString，屏幕出现 `NetError$BusinessError: System error…`；
 * ② 改成带 errno/HTTP 码的「更友好」文案，用户点名这是把接口和错误直接打印到端上；
 * ③ 网络层还把 NetError 包在 ExecutionException 里，不剥包装整个分支形同虚设。
 * 钉三条规矩：技术细节（errno、HTTP 码、服务端英文原文）一律不上屏；
 * 服务端错不说成网络错；包装过的异常也要走到同一套文案。
 */
class FeedErrorCopyTest {

    private fun assertNoDebugLeaks(text: String) {
        assertTrue(text, !text.contains("errno"))
        assertTrue(text, !text.contains("NetError"))
        assertTrue(text, !text.contains("System error"))
        assertTrue(text, !Regex("\\d{3}").containsMatchIn(text))
    }

    @Test
    fun `business error blames the server without leaking errno or server copy`() {
        val text = friendlyFeedError(NetError.BusinessError(10000, "System error，please try again"))
        assertTrue(text, text.contains("服务端"))
        // 不许把服务端错误说成网络问题
        assertTrue(text, !text.contains("网络"))
        assertNoDebugLeaks(text)
    }

    @Test
    fun `server error hides the http status`() {
        val text = friendlyFeedError(NetError.ServerError(502, null))
        assertTrue(text, text.contains("服务端"))
        assertNoDebugLeaks(text)
    }

    @Test
    fun `network failure is the only one that mentions the network`() {
        val text = friendlyFeedError(NetError.NetworkError(java.io.IOException("no route")))
        assertTrue(text, text.contains("网络"))
        assertNoDebugLeaks(text)
    }

    /**
     * 真机回归：`awaitInfoList` 把 NetError 包在 ExecutionException 里再抛，
     * 上一版 `friendlyFeedError` 直接 when(cause) 永远落 else，新文案形同虚设。
     */
    @Test
    fun `error wrapped in ExecutionException still gets friendly copy`() {
        val wrapped = java.util.concurrent.ExecutionException(
            NetError.BusinessError(10000, "System error，please try again"),
        )
        val text = friendlyFeedError(wrapped)
        assertTrue(text, text.contains("服务端"))
        assertTrue(text, !text.contains("网络"))
        assertNoDebugLeaks(text)
    }
}
