package com.openapi.feature.chat.data.remote

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A39：附件回合的等待必须有限——`awaitTurnText` 现在复用 [TurnEventCollector]。
 *
 * 用户报告「agent 感觉一直不结束，一直在生成」。主线文字路径 20261124 已配帧间看门狗
 * （真机 logcat 20261125 22:05:12 `W ChatStream: stream error: 等不到新的内容` 证实它在
 * 1.1.0.72 上开火），但**附件路径**的 `awaitTurnText` 是另一只手搓的 collect：
 * readTimeout=0、心跳注释不产生帧——「连接活着、模型不出字」时 `attachmentJob` 永远不结束，
 * `streaming=attachmentActive()` 永真，「正在生成」永不落地。这里钉三件事：
 * 1. 只发心跳永不出内容的流 → 看门狗在虚拟时间到点后**返回**（旧实现会挂死 runTest）；
 * 2. 正常 delta+done → 全文原样拿到（委托没有改变成功语义）；
 * 3. 服务端 error 帧 → 已收到的半截照常交出去，不吞也不挂。
 *
 * 用注入的 [AttachmentApi.eventSource] 喂帧，不起真连接；`remote` 只为满足构造
 * （Robolectric 给 Context，构造不开网络）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AttachmentTurnWatchdogTest {

    private fun api(streams: (attempt: Int) -> Flow<AgentSseFrame>): AttachmentApi {
        val remote = AgentChatRemote(
            context = ApplicationProvider.getApplicationContext(),
            baseUrl = "http://127.0.0.1",
            tokenProvider = { "token" },
        )
        return AttachmentApi(remote, eventSource = { _, _ -> streams(1) })
    }

    private fun frame(id: String?, event: String, data: String) = AgentSseFrame(id, event, data)

    @Test
    fun idleForeverStreamSettlesViaTheWatchdogInsteadOfHanging() = runTest {
        // 真实形状：服务端只发 `:` 心跳注释（不产生帧），模型一个字不出。
        // 旧实现在这里永远 collect 着；新实现必须在 45s（虚拟）看门狗后交回空串。
        // runTest 会自动推进虚拟时间——若实现挂死，本用例会以超时失败而不是通过。
        val text = api { flow { awaitCancellation() } }.awaitTurnText(1L, "T1")
        assertEquals("", text)
    }

    @Test
    fun deltasAndDoneReturnTheFullText() = runTest {
        val text = api {
            flow {
                emit(frame("1-0", "message.delta", """{"turn_id":"T1","delta":"你好"}"""))
                emit(frame("2-0", "message.delta", """{"turn_id":"T1","delta":"，我是林澈"}"""))
                emit(frame("3-0", "message.done", """{"turn_id":"T1","assistant_message_id":88}"""))
            }
        }.awaitTurnText(1L, "T1")
        assertEquals("你好，我是林澈", text)
    }

    @Test
    fun errorFrameKeepsThePartialText() = runTest {
        val text = api {
            flow {
                emit(frame("1-0", "message.delta", """{"turn_id":"T1","delta":"说了一半"}"""))
                emit(frame("2-0", "error", """{"turn_id":"T1","code":"upstream_overload"}"""))
            }
        }.awaitTurnText(1L, "T1")
        assertEquals("说了一半", text)
    }
}
