package com.openapi.feature.video.screening

import com.openapi.common.net.http.NetError
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 取数失败文案：服务端自己报错时，绝不能说得像用户网络坏了。
 *
 * 真机撞过：接口 500 兜底成 errno 10000，屏幕上一句「没取到服务端数据：… 网络问题」，
 * 用户跑去查明明正常的网络。这里按 NetError 子类型分支，钉住两类说法不串。
 */
class FeedErrorCopyTest {

    @Test
    fun `business error blames the server and carries errno`() {
        val text = friendlyFeedError(NetError.BusinessError(10000, "System error，please try again"))
        assertTrue(text, text.startsWith("服务端返回错误"))
        assertTrue(text, text.contains("10000"))
        // 不许把服务端错误说成网络问题
        assertTrue(text, !text.contains("网络"))
    }

    @Test
    fun `server error names the http status`() {
        val text = friendlyFeedError(NetError.ServerError(502, null))
        assertTrue(text, text.contains("502"))
        assertTrue(text, !text.contains("网络"))
    }

    @Test
    fun `network failure is the only one that mentions the network`() {
        val text = friendlyFeedError(NetError.NetworkError(java.io.IOException("no route")))
        assertTrue(text, text.contains("网络"))
    }

    /**
     * 真机回归：`awaitInfoList` 把 NetError 包在 ExecutionException 里再抛，
     * 上一版 `friendlyFeedError` 直接 when(cause) 永远落 else，新文案形同虚设——
     * 用户截图里旧文案「没取到服务端数据: …BusinessError: System error」复活就是这个原因。
     */
    @Test
    fun `error wrapped in ExecutionException still gets friendly copy`() {
        val wrapped = java.util.concurrent.ExecutionException(
            NetError.BusinessError(10000, "System error，please try again"),
        )
        val text = friendlyFeedError(wrapped)
        assertTrue(text, text.startsWith("服务端返回错误"))
        assertTrue(text, text.contains("10000"))
        assertTrue(text, !text.contains("网络"))
    }
}
