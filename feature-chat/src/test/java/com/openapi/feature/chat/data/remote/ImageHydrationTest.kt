package com.openapi.feature.chat.data.remote

import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.ImageAttachment
import com.openapi.feature.chat.domain.model.ImageSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * 「切换会话慢」的关键路径之一：一页历史里的远程图片要逐张换签名 URL，
 * 每张一次 `GET /uploads/{id}`（0.4–2s）。串行做时 5 张图就是 2–10 秒的白屏，
 * 而且整页历史都等它——签名 URL 是渲染的前置条件，不是可推迟的装饰。
 *
 * 这里守三件事：
 * 1. 取 URL 必须并发（最大同时在途数 == 不同 id 数）；
 * 2. 同一张图在一页里出现多次只取一次（distinct）；
 * 3. 单张失败退化为空路径，不拖垮整页（这条以前就有，防止并发化时改掉）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImageHydrationTest {

    private fun remoteMsg(id: String, uploadIds: List<String>) = ChatMessage(
        id = id, role = ChatRole.USER, text = "", createdAtMillis = 1L,
        images = uploadIds.map { ImageAttachment(path = "", source = ImageSource.REMOTE, uploadId = it) },
    )

    private fun textMsg(id: String) =
        ChatMessage(id = id, role = ChatRole.USER, text = "纯文字", createdAtMillis = 1L)

    /** 并发探针：每次在途 +1，记录峰值，挂起一段时间，离开 -1。
     *  虚拟时间下 delay 不花真实时间；串行取 URL 时峰值必然是 1。 */
    private class Probe {
        val inFlight = AtomicInteger(0)
        var maxInFlight = 0
        val calls = mutableListOf<String>()
        var failFor: String? = null

        suspend fun fetch(id: String): String? {
            inFlight.incrementAndGet()
            synchronized(this) { maxInFlight = maxOf(maxInFlight, inFlight.get()) }
            delay(1_000)
            synchronized(this) { calls += id }
            inFlight.decrementAndGet()
            if (id == failFor) return null
            return "https://signed/$id"
        }
    }

    @Test
    fun signedUrlsAreFetchedConcurrentlyNotOneAfterAnother() = runTest(StandardTestDispatcher()) {
        val probe = Probe()
        val messages = listOf(remoteMsg("m1", listOf("u1", "u2")), remoteMsg("m2", listOf("u3")))
        val out = hydrateRemoteImages(messages) { probe.fetch(it) }
        advanceUntilIdle()

        assertEquals("三个不同 id 必须同时在途", 3, probe.maxInFlight)
        assertEquals(3, probe.calls.size)
        // 结果仍然对得上各自的图
        assertEquals("https://signed/u1", out[0].images[0].path)
        assertEquals("https://signed/u3", out[1].images[0].path)
    }

    @Test
    fun theSameUploadAcrossAPageIsFetchedOnce() = runTest(StandardTestDispatcher()) {
        val probe = Probe()
        val messages = listOf(remoteMsg("m1", listOf("u1")), remoteMsg("m2", listOf("u1")))
        val out = hydrateRemoteImages(messages) { probe.fetch(it) }
        advanceUntilIdle()

        assertEquals(listOf("u1"), probe.calls)
        assertEquals("https://signed/u1", out[0].images[0].path)
        assertEquals("https://signed/u1", out[1].images[0].path)
    }

    @Test
    fun oneFailedUrlDegradesToEmptyPathNotAFailedPage() = runTest(StandardTestDispatcher()) {
        val probe = Probe()
        probe.failFor = "u2"
        val messages = listOf(remoteMsg("m1", listOf("u1", "u2")))
        val out = hydrateRemoteImages(messages) { probe.fetch(it) }
        advanceUntilIdle()

        assertEquals("https://signed/u1", out[0].images[0].path)
        assertEquals("", out[0].images[1].path)
    }

    @Test
    fun weakNetworkThrowDegradesToEmptyPathLikeNull() = runTest(StandardTestDispatcher()) {
        // 断网/弱网进页时 fetch 抛的是异常（超时、连接重置），不是 null。
        // P1-10 的失败占位依赖这条：抛异常的那格退化成空 path 进气泡画占位，
        // 同一页其它图照常签名成功——整页不能被一张图的失败拖垮。
        val probe = Probe()
        val messages = listOf(remoteMsg("m1", listOf("u1", "u2")), remoteMsg("m2", listOf("u3")))
        val out = hydrateRemoteImages(messages) { id ->
            if (id == "u2") throw java.io.IOException("offline")
            probe.fetch(id)
        }
        advanceUntilIdle()

        assertEquals("https://signed/u1", out[0].images[0].path)
        assertEquals("抛异常的那格退化为空 path（组件据此画失败占位）", "", out[0].images[1].path)
        assertEquals("同页另一条不受影响", "https://signed/u3", out[1].images[0].path)
    }

    @Test
    fun messagesWithoutRemoteImagesAreUntouched() = runTest(StandardTestDispatcher()) {
        val probe = Probe()
        val local = ChatMessage(id = "m1", role = ChatRole.USER, text = "", createdAtMillis = 1L,
            images = listOf(ImageAttachment(path = "/tmp/a.jpg", source = ImageSource.FILE)))
        val out = hydrateRemoteImages(listOf(textMsg("m0"), local, remoteMsg("m2", listOf("u1")))) { probe.fetch(it) }
        advanceUntilIdle()

        assertEquals(listOf("u1"), probe.calls)
        assertEquals("/tmp/a.jpg", out[1].images[0].path)
        assertTrue(out[0].images.isEmpty())
    }

    @Test
    fun aMessageWithNoUploadIdStaysEmpty() = runTest(StandardTestDispatcher()) {
        // uploadId 为 null 的远程图没有可查的东西：保持空路径，不发请求。
        val probe = Probe()
        val msg = ChatMessage(id = "m1", role = ChatRole.USER, text = "", createdAtMillis = 1L,
            images = listOf(ImageAttachment(path = "", source = ImageSource.REMOTE, uploadId = null)))
        val out = hydrateRemoteImages(listOf(msg)) { probe.fetch(it) }
        advanceUntilIdle()
        assertTrue(probe.calls.isEmpty())
        assertEquals("", out[0].images[0].path)
    }
}
