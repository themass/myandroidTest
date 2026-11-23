package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.MessageKind
import com.openapi.feature.chat.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * S3「草稿托盘再编辑」的解析接缝（[SendActions.draftImageForEdit]）。
 *
 * 这条接缝是长按入口能不能打开编辑器的唯一判断点，所以它必须自己钉住四件事：
 * 图片草稿可编辑、文件已丢不可编辑、非图片草稿不可编辑、id 找不到不可编辑。
 * 后三种情形界面都不该弹错误——草稿在长按瞬间被发出或被移除是正常竞态，
 * 报错对用户就是噪音；这条规则在这里断言，而不是在 Compose 里靠肉眼确认。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DraftTrayEditTest {

    @get:Rule
    val folder = TemporaryFolder()

    private class Harness(initial: ChatUiState) {
        val repo = FakeChatRepository()
        val state = MutableStateFlow(initial)
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
    }

    private fun draft(
        id: String,
        kind: MessageKind,
        file: java.io.File,
    ) = DraftAttachment(
        id = id, kind = kind,
        staged = StagedAttachment(file = file, displayName = file.name, sizeBytes = 1L),
    )

    private fun harnessWith(vararg drafts: DraftAttachment) =
        Harness(ChatUiState(input = "带图", drafts = drafts.toList()))

    @Test
    fun imageDraftWithExistingFileIsEditable() {
        val file = folder.newFile("shot.jpg")
        val h = harnessWith(draft("d1", MessageKind.IMAGE, file))

        assertEquals(file, h.send.draftImageForEdit("d1"))
    }

    @Test
    fun imageDraftWhoseFileVanishedIsNotEditable() {
        // 暂存文件被 GC 扫掉（AttachmentStager 的回收）或用户清理过缓存：
        // 这时候打开编辑器只能得到一张黑图，不如不打开。
        val missing = java.io.File(folder.root, "gone.jpg")
        val h = harnessWith(draft("d1", MessageKind.IMAGE, missing))

        assertNull(h.send.draftImageForEdit("d1"))
    }

    @Test
    fun videoDraftIsNotEditable() {
        val file = folder.newFile("clip.mp4")
        val h = harnessWith(draft("v1", MessageKind.VIDEO, file))

        // 只有图片有编辑语义；长按视频什么都不发生
        assertNull(h.send.draftImageForEdit("v1"))
    }

    @Test
    fun fileDraftIsNotEditable() {
        val file = folder.newFile("report.pdf")
        val h = harnessWith(draft("f1", MessageKind.FILE, file))

        assertNull(h.send.draftImageForEdit("f1"))
    }

    @Test
    fun unknownDraftIdIsNotEditable() {
        val file = folder.newFile("shot.jpg")
        val h = harnessWith(draft("d1", MessageKind.IMAGE, file))

        // 草稿已经被发出或移除：竞态，不是错误
        assertNull(h.send.draftImageForEdit("nope"))
    }

    @Test
    fun resolvesTheRightDraftAmongMany() {
        val first = folder.newFile("a.jpg")
        val second = folder.newFile("b.jpg")
        val third = folder.newFile("c.jpg")
        val h = harnessWith(
            draft("d1", MessageKind.IMAGE, first),
            draft("d2", MessageKind.IMAGE, second),
            draft("d3", MessageKind.IMAGE, third),
        )

        assertEquals(second, h.send.draftImageForEdit("d2"))
    }

    @Test
    fun editResolvingDoesNotTouchDraftState() {
        // 解析必须是只读的：长按一次图片不该把草稿清掉或改动输入框
        val file = folder.newFile("shot.jpg")
        val h = harnessWith(draft("d1", MessageKind.IMAGE, file))
        val before = h.state.value

        h.send.draftImageForEdit("d1")

        assertEquals(before, h.state.value)
    }
}
