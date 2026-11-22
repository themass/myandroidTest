package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.DeliveryStatus
import com.openapi.feature.chat.domain.model.FileAttachment
import com.openapi.feature.chat.domain.model.MessageKind
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerAndFeedbackTest {

    private fun msg(id: String, role: ChatRole, text: String, files: List<FileAttachment> = emptyList()) =
        ChatMessage(id = id, role = role, text = text, createdAtMillis = 1L, files = files, delivery = DeliveryStatus.SENT)

    @Test
    fun inputChangeIsKept() {
        val state = MutableStateFlow(ChatUiState())
        ComposerActions(state).setInput("你好")
        assertEquals("你好", state.value.input)
    }

    @Test
    fun editStartsOnlyForPlainUserText() {
        val state = MutableStateFlow(
            ChatUiState(
                messages = listOf(
                    msg("u1", ChatRole.USER, "原文"),
                    msg("a1", ChatRole.ASSISTANT, "回复"),
                    msg("u2", ChatRole.USER, "带文件", files = listOf(FileAttachment(path = "/tmp/a.txt", name = "a.txt", sizeBytes = 1L))),
                ),
            ),
        )
        val composer = ComposerActions(state)
        composer.startEdit("a1") // 助手消息不能编辑
        assertNull(state.value.editingMessageId)
        composer.startEdit("u2") // 带文件的不能编辑
        assertNull(state.value.editingMessageId)
        composer.startEdit("u1")
        assertEquals("u1", state.value.editingMessageId)
        assertEquals("原文", state.value.input)
        composer.cancelEdit()
        assertNull(state.value.editingMessageId)
        assertEquals("", state.value.input)
    }

    @Test
    fun dismissErrorClearsIt() {
        val state = MutableStateFlow(ChatUiState(error = "出错了"))
        ComposerActions(state).dismissError()
        assertNull(state.value.error)
    }

    @Test
    fun draftsCanBeAddedAndRemoved() {
        val state = MutableStateFlow(ChatUiState())
        val composer = ComposerActions(state)
        composer.addDraft(MessageKind.IMAGE, StagedAttachment(file = java.io.File("/tmp/a.jpg"), displayName = "a.jpg", sizeBytes = 1L))
        composer.addDraft(MessageKind.IMAGE, StagedAttachment(file = java.io.File("/tmp/b.jpg"), displayName = "b.jpg", sizeBytes = 1L))
        assertEquals(2, state.value.drafts.size)
        val firstId = state.value.drafts.first().id
        composer.removeDraft(firstId)
        assertEquals(1, state.value.drafts.size)
        assertFalse(state.value.drafts.any { it.id == firstId })
    }

    /**
     * 终评 I-1：图片草稿的数量闸。
     *
     * 钉三件事：
     * 1. 到第 9 张为止都能进（上限本身没被抄成一个别的数字）；
     * 2. 第 10 张**进不去**，而且 `addDraft` 把「因为到顶所以没进」这个事实**回出来**——
     *    回不出来的话调用方就没法报错，用户只会看到图被吞了；
     * 3. 闸只管图片：文件/视频/位置不受这条规则牵连（服务端裁的是 attachments，
     *    而「文件」走的是 legacy 逐条接口，把 9 张的额度算在文件上是另一种错）。
     *
     * 为什么必须在**这里**拦而不是只靠选择器的 `maxItems`：那是建议值，厂商 ROM 的
     * 选择器会不认；只靠它的话，超出的图要先把上传流量花完，再被服务端的 422 打回来。
     */
    @Test
    fun imageDraftsStopAtTheCapAndSaySo() {
        val state = MutableStateFlow(ChatUiState())
        val composer = ComposerActions(state)
        fun add(i: Int, kind: MessageKind = MessageKind.IMAGE) =
            composer.addDraft(kind, StagedAttachment(file = java.io.File("/tmp/$i"), displayName = "$i", sizeBytes = 1L))

        for (i in 1..com.openapi.common.media.MAX_MESSAGE_IMAGES) {
            assertTrue("第 $i 张应该能进", add(i))
        }
        assertEquals(com.openapi.common.media.MAX_MESSAGE_IMAGES, state.value.drafts.size)

        assertFalse("第 10 张必须被拦下", add(10))
        assertEquals("拦下就是没进", com.openapi.common.media.MAX_MESSAGE_IMAGES, state.value.drafts.size)

        // 删掉一张之后又能进：闸数的是**当前**张数，不是一次会话的累计
        composer.removeDraft(state.value.drafts.first().id)
        assertTrue("腾出位置后要能再进", add(11))
    }

    /** 闸只数图片：文件与视频各有自己的规则，不能被图片的 9 张牵连。 */
    @Test
    fun theImageCapDoesNotAffectFilesOrVideos() {
        val state = MutableStateFlow(
            ChatUiState(
                drafts = (1..com.openapi.common.media.MAX_MESSAGE_IMAGES).map {
                    DraftAttachment(
                        id = "d$it",
                        kind = MessageKind.IMAGE,
                        staged = StagedAttachment(file = java.io.File("/tmp/i$it.jpg"), displayName = "i$it.jpg", sizeBytes = 1L),
                    )
                },
            ),
        )
        val composer = ComposerActions(state)
        assertTrue("图片满格时文件照样能进", composer.addDraft(MessageKind.FILE, StagedAttachment(file = java.io.File("/tmp/a.txt"), displayName = "a.txt", sizeBytes = 1L)))
        assertTrue("图片满格时视频照样能进", composer.addDraft(MessageKind.VIDEO, StagedAttachment(file = java.io.File("/tmp/a.mp4"), displayName = "a.mp4", sizeBytes = 1L)))
        assertEquals(com.openapi.common.media.MAX_MESSAGE_IMAGES + 2, state.value.drafts.size)
    }

    @Test
    fun likeAndDislikeAreMutuallyExclusive() {
        val state = MutableStateFlow(ChatUiState())
        val feedback = FeedbackActions(state)
        assertTrue(feedback.toggleLike("a1"))
        assertEquals(setOf("a1"), state.value.likedIds)
        feedback.disliked("a1")
        assertEquals(setOf("a1"), state.value.dislikedIds)
        assertTrue(state.value.likedIds.isEmpty())
        // 再点赞：取消点踩，返回 true（现在是已赞）
        assertTrue(feedback.toggleLike("a1"))
        assertTrue(state.value.dislikedIds.isEmpty())
        // 再点一次：取消点赞，返回 false
        assertFalse(feedback.toggleLike("a1"))
        assertTrue(state.value.likedIds.isEmpty())
    }
}
