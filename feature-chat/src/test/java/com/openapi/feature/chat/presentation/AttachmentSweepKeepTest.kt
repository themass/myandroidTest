package com.openapi.feature.chat.presentation

import androidx.test.core.app.ApplicationProvider
import com.openapi.common.media.StagingGc
import com.openapi.feature.chat.domain.model.MessageKind
import com.openapi.feature.chat.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 复审 Important：暂存清扫**不能删掉本地行还在引用的字节**。
 *
 * `chat_attachments` 与陪伴页那个目录不一样：那里的文件不一定都是孤儿。一条 `local-` 行只要
 * 没到 SENT（上传一次都没成功过），它的图片字节就**只存在本机**：
 * - `LocalChatStore.localPending` 会把这些行重新捞回界面（冷启动恢复），气泡上有重试图标；
 * - 重发的那条路（`ResendActions.retry → AttachmentSender.deliverBody`）先 `File(image.path)`
 *   读字节重新上传，只有文件已经没了且这条**曾经传成功过**才退回 `uploadId`
 *   （ADR-011：直传 id 只活 10 分钟，隔一夜必然 410——`AttachmentSender` 的注释就是
 *   「有字节在手就别赌它还能用」）；而 id 是**全部上传成功后**才写回这一行的，
 *   所以「从未上传成功」的行手里只有字节。
 * 按年龄把这种字节扫掉，等于让那条消息**永久**重发不出去——比原来的「只增不减」更糟。
 *
 * 这里钉的是**调用点**的行为（清扫逻辑本身在 :common 有用例）：从选择器那条路进暂存目录时，
 * 被非 SENT 本地行引用的旧文件必须留下，同目录里没人引用的旧文件照旧走。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AttachmentSweepKeepTest {

    private val stale = System.currentTimeMillis() - StagingGc.MAX_AGE_MILLIS - 60_000

    private class Harness(private val dir: File) {
        val repo = FakeChatRepository()
        val state = MutableStateFlow(ChatUiState())
        val scope = TestScope(StandardTestDispatcher())
        val send: SendActions

        init {
            val streamer = ReplyStreamer(
                repo, state, scope, SendMessageUseCase(repo),
                attachmentActive = { false }, reconcile = {}, syncSessions = {},
                strings = FakeChatStrings(),
            )
            send = SendActions(
                repo, state, scope, ComposerActions(state), streamer,
                submitEdit = { _, _ -> },
                startAttachmentSend = { },
                strings = FakeChatStrings(),
            )
        }

        /** 本地行引用的那条路径（库里查出来的形状就是这一个集合）。 */
        fun referenceUnsent(fileName: String) {
            repo.attachmentPathsInUse = setOf(File(dir, fileName).absolutePath)
        }

        fun stageAnImage() {
            // 没有 provider 的 uri：解码必然失败（StageOutcome.Failed），
            // 但暂存目录已经被打开并扫过一遍——清扫与解码成不成功无关。
            // ioDispatcher 传测试调度器：默认那个是 Dispatchers.IO 真线程，
            // advanceUntilIdle 追不到它，断言会在清扫还没跑完时就读目录（假绿）。
            send.attachmentPicked(
                ApplicationProvider.getApplicationContext(),
                MessageKind.IMAGE,
                android.net.Uri.parse("content://no.such.provider/1"),
                ioDispatcher = UnconfinedTestDispatcher(scope.testScheduler),
            )
            scope.advanceUntilIdle()
        }
    }

    private fun staleFile(dir: File, name: String): File =
        File(dir, name).apply {
            writeText("bytes")
            assertTrue("设 mtime 失败", setLastModified(stale))
        }

    @Test
    fun `a stale file a non-SENT local row still references survives the sweep`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dir = File(context.filesDir, "chat_attachments").apply { mkdirs() }
        val referenced = staleFile(dir, "img_referenced.jpg")
        val orphan = staleFile(dir, "img_orphan.jpg")
        val fresh = File(dir, "img_fresh.jpg").apply { writeText("bytes") }

        val h = Harness(dir)
        h.referenceUnsent("img_referenced.jpg")
        h.stageAnImage()

        assertTrue("被非 SENT 本地行引用的字节必须留下——重发全靠它", referenced.exists())
        assertFalse("同目录里没人引用的超龄孤儿照旧走", orphan.exists())
        assertTrue("没到龄的文件不能碰", fresh.exists())
        // 白名单是问出来的，不是硬编码出来的（复审 nit 5）：一次选图问一次仓库。
        // 问两次以上说明清扫在循环里跑（每张图扫一遍整个目录），那是性能回归的形状。
        assertEquals("一次选图只问一次仓库白名单", 1, h.repo.attachmentPathsInUseCalls.size)
    }

    @Test
    fun `a stale file sitting in the live tray survives the sweep`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dir = File(context.filesDir, "chat_attachments").apply { mkdirs() }
        val inTray = staleFile(dir, "img_in_tray.jpg")

        // 托盘是进程内的：App 一直开着、草稿压了一周没发，此时又去选了一张图。
        // 没有任何本地行引用它，但删掉它用户得到的是「发不出去的破卡片」。
        val h = Harness(dir)
        h.state.value = h.state.value.copy(
            drafts = listOf(
                DraftAttachment(
                    id = "draft-1",
                    kind = MessageKind.IMAGE,
                    staged = StagedAttachment(file = inTray, displayName = inTray.name, sizeBytes = 5L),
                ),
            ),
        )
        h.stageAnImage()

        assertTrue("托盘里那张还没发的图不能被扫掉", inTray.exists())
    }
}
