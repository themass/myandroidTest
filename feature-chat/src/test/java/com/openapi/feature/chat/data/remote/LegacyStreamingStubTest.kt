package com.openapi.feature.chat.data.remote

import com.openapi.feature.chat.domain.model.ChatStreamEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 老 Java 链路的第二套流式实现（`/chat/stream.json` 的 SSE 客户端 ChatStreamClient）已删除：
 * 运行时流式一律走 AgentBackedChatRepository → /v1（ChatUseCases/ReplyStreamer 都取 ChatDependencies）。
 * 这个测试钉住契约：ChatRepositoryImpl 的流式接口只剩「明确报错」的桩，
 * 绝不再对旧端点发起网络请求——事件文案就是这个桩的指纹。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegacyStreamingStubTest {

    private lateinit var repo: ChatRepositoryImpl

    @Before
    fun setUp() {
        com.openapi.core.app.AppContext.init(RuntimeEnvironment.getApplication())
        repo = ChatRepositoryImpl(RuntimeEnvironment.getApplication())
    }

    @Test
    fun sendMessageStreamIsTerminalErrorInsteadOfLegacyNetwork() = runTest {
        val events = repo.sendMessageStream(
            sessionId = 1,
            userText = "你好",
            characterId = null,
            roleId = null,
            persistUserMessage = true,
            clientMessageId = "local-1",
            attachments = emptyList(),
        ).toList()

        val error = events.single() as ChatStreamEvent.Error
        assertTrue("必须是停用桩的文案，而不是旧 SSE 客户端的报错: ${error.message}",
            error.message.contains("已停用"))
        // 桩没有服务端用户消息 id：重试会退回本地 id 重发文字，这正是 /v1 链路的兜底语义
        assertEquals(null, error.userMessageId)
    }

    @Test
    fun regenerateStreamIsTerminalErrorToo() = runTest {
        val events = repo.regenerateStream(sessionId = 1, characterId = null, roleId = null).toList()

        assertTrue(events.single() is ChatStreamEvent.Error)
    }
}
