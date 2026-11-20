package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.ImageAttachment
import com.openapi.feature.chat.domain.model.ImageSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 图片重试的数据通路（整改清单 P1-10）：点失败的远程图 → 用 `GET /uploads/{id}`
 * 重新签一个 URL → 只有那一格的 path 被换掉；本机文件、没有 uploadId 的图不发请求；
 * 同一张图在途时连点不重复请求；换不到 URL 时什么都不改（失败占位留着让用户再点）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImageRetryActionsTest {

    private class Harness(messages: List<ChatMessage>) {
        val repo = FakeChatRepository()
        val state = MutableStateFlow(ChatUiState(messages = messages))
        val scope = TestScope(StandardTestDispatcher())
        val actions = ImageRetryActions(repo, state, scope)
    }

    private fun remoteMsg(id: String, paths: List<String>, uploadIds: List<String?>) = ChatMessage(
        id = id, role = ChatRole.USER, text = "", createdAtMillis = 1L,
        images = paths.zip(uploadIds) { p, u -> ImageAttachment(path = p, source = ImageSource.REMOTE, uploadId = u) },
    )

    private fun localMsg(id: String, path: String) = ChatMessage(
        id = id, role = ChatRole.USER, text = "", createdAtMillis = 1L,
        images = listOf(ImageAttachment(path = path, source = ImageSource.FILE)),
    )

    @Test
    fun retryingARemoteImageReplacesOnlyThatImagePath() {
        val h = Harness(
            listOf(
                remoteMsg("m1", listOf("", "https://old/u2"), listOf("u1", "u2")),
                remoteMsg("m2", listOf("https://keep/u3"), listOf("u3")),
            ),
        )
        h.repo.refreshImageUrlResult = "https://new/u1"
        h.actions.retry("m1", 0)
        h.scope.advanceUntilIdle()

        assertEquals(listOf("u1"), h.repo.refreshImageUrlCalls)
        assertEquals("https://new/u1", h.state.value.messages[0].images[0].path)
        assertEquals("同一条消息里的另一张不动", "https://old/u2", h.state.value.messages[0].images[1].path)
        assertEquals("别的消息不动", "https://keep/u3", h.state.value.messages[1].images[0].path)
    }

    @Test
    fun failedResignKeepsMessageUntouchedSoTheUserCanTapAgain() {
        val h = Harness(listOf(remoteMsg("m1", listOf(""), listOf("u1"))))
        h.repo.refreshImageUrlResult = null // 旧服务端 / 对象已删 / 网络失败
        h.actions.retry("m1", 0)
        h.scope.advanceUntilIdle()

        assertEquals(listOf("u1"), h.repo.refreshImageUrlCalls)
        assertEquals("换不到就不改 path：失败占位留在原地", "", h.state.value.messages[0].images[0].path)
    }

    @Test
    fun doubleTapWhileInFlightOnlyAsksTheServerOnce() {
        val h = Harness(listOf(remoteMsg("m1", listOf(""), listOf("u1"))))
        h.repo.refreshImageUrlResult = "https://new/u1"
        h.actions.retry("m1", 0)
        h.actions.retry("m1", 0) // 第一次还没回来
        h.scope.advanceUntilIdle()

        assertEquals("在途去重：两次点击只发一次请求", listOf("u1"), h.repo.refreshImageUrlCalls)
    }

    @Test
    fun afterOneFinishesTheNextTapIsAllowedAgain() {
        val h = Harness(listOf(remoteMsg("m1", listOf(""), listOf("u1"))))
        h.repo.refreshImageUrlResult = null
        h.actions.retry("m1", 0)
        h.scope.advanceUntilIdle()
        h.actions.retry("m1", 0)
        h.scope.advanceUntilIdle()
        assertEquals("上一次失败结束后可以重试点第二次", listOf("u1", "u1"), h.repo.refreshImageUrlCalls)
    }

    @Test
    fun localFileImagesNeverHitTheServer() {
        // 本机文件的重读由组件自己完成（换 Coil 缓存 key），这里不该产生任何网络请求
        val h = Harness(listOf(localMsg("m1", "/data/local/a.jpg")))
        h.actions.retry("m1", 0)
        h.scope.advanceUntilIdle()
        assertEquals(emptyList<String>(), h.repo.refreshImageUrlCalls)
    }

    @Test
    fun remoteImageWithoutUploadIdIsNotRequested() {
        val h = Harness(listOf(remoteMsg("m1", listOf(""), listOf(null as String?))))
        h.actions.retry("m1", 0)
        h.scope.advanceUntilIdle()
        assertEquals(emptyList<String>(), h.repo.refreshImageUrlCalls)
    }

    @Test
    fun outOfRangeIndexAndUnknownMessageAreNoOps() {
        val h = Harness(listOf(remoteMsg("m1", listOf(""), listOf("u1"))))
        h.actions.retry("m1", 7)
        h.actions.retry("nope", 0)
        h.scope.advanceUntilIdle()
        assertEquals(emptyList<String>(), h.repo.refreshImageUrlCalls)
    }

    @Test
    fun twoMessagesRetryIndependently() {
        // 一条消息的重试不影响另一条：两条各自拿各自的 URL
        val h = Harness(
            listOf(
                remoteMsg("m1", listOf(""), listOf("u1")),
                remoteMsg("m2", listOf(""), listOf("u2")),
            ),
        )
        h.repo.refreshImageUrlResult = "https://new/x"
        h.actions.retry("m1", 0)
        h.actions.retry("m2", 0)
        h.scope.advanceUntilIdle()
        assertEquals(listOf("u1", "u2"), h.repo.refreshImageUrlCalls)
        assertEquals("https://new/x", h.state.value.messages[0].images[0].path)
        assertEquals("https://new/x", h.state.value.messages[1].images[0].path)
    }
}
