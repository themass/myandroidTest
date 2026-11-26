package com.openapi.feature.chat.presentation.ui

import com.openapi.chatkit.model.MessagePart
import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.ImageAttachment
import com.openapi.feature.chat.domain.model.VoiceAttachment
import com.openapi.speech.audio.AudioPlayback
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A43-2/#11 第一层兜底：**空的助手行不进列表**（用户投诉：点停止/取消后对话里留一个
 * 只有 footer 没有内容的空 AI 气泡）。
 *
 * 空行的来源不止「取消」一条：历史侧 kind=IMAGE 但图集被 ChatLogMapper 全部丢空
 * （塌缩到 0 条）、服务端存了无内容的回复等，都会让 ChatItemMapper 造出一个
 * parts 为空的 ChatItem——chatkit 会在这一行画 footer/头像占位，用户看到空气泡。
 * （kind=TEXT 且 text 空的历史行 ChatLogMapper 已经 return null，这里补上其余 kind。）
 *
 * 放行的形状（绝不能误吞）：
 * - pending + 无文字 → thinking=true 的占位气泡（三个跳动点）——这是 A36 的合法占位；
 * - pending + 有文字 → streaming=true 的流式气泡；
 * - 语音转写中（voice != null && pending）→ Voice part 本身就在，跟 text 空不空无关。
 */
class ChatItemMapperEmptyAssistantDropTest {

    private fun map(vararg messages: ChatMessage) = ChatItemMapper.map(
        messages = messages.toList(),
        playback = AudioPlayback(),
        speakingId = null,
        likedIds = emptySet(),
        dislikedIds = emptySet(),
    )

    @Test
    fun anAssistantRowWithNothingToRenderIsDropped() {
        // kind 无关：text 空、无附件、无语音、非 pending —— 没有任何 part 可画
        val items = map(
            ChatMessage(id = "a1", role = ChatRole.ASSISTANT, text = "", createdAtMillis = 1L),
        )
        assertEquals("空助手行必须被丢掉：不丢它就会画一个只有 footer 的空气泡", 0, items.size)
    }

    @Test
    fun aHistoryImageRowWhoseGalleryCollapsedToZeroIsDroppedToo() {
        // 历史 kind=IMAGE、images 全被丢空（ChatLogMapper 塌缩的极端）：同样不该进气泡列表
        val items = map(
            ChatMessage(id = "a2", role = ChatRole.ASSISTANT, text = "", createdAtMillis = 1L, images = emptyList()),
        )
        assertEquals(0, items.size)
    }

    @Test
    fun emptyThinkingPlaceholderSurvives() {
        // pending + 空文字 = 「正在想」占位：parts 也是空，但**必须保留**（thinking 气泡靠它）
        val items = map(
            ChatMessage(id = "p1", role = ChatRole.ASSISTANT, text = "", createdAtMillis = 1L, pending = true),
        )
        val item = items.single()
        assertEquals(0, item.parts.size)
        assertEquals("占位必须映射成 thinking，不能当空行吞掉", true, item.thinking)
    }

    @Test
    fun streamingPlaceholderSurvives() {
        // pending + 已有文字 = 流式：parts 非空，照常保留
        val items = map(
            ChatMessage(id = "s1", role = ChatRole.ASSISTANT, text = "写到一半", createdAtMillis = 1L, pending = true),
        )
        val item = items.single()
        assertEquals(true, item.streaming)
        assertEquals(listOf(MessagePart.Text("写到一半")), item.parts)
    }

    @Test
    fun voiceTranscribingAssistantSurvives() {
        // 语音转写中：text 空但 voice part 在——放行与 pending 语义都靠 Voice part 本身
        val items = map(
            ChatMessage(
                id = "v1",
                role = ChatRole.ASSISTANT,
                text = "",
                createdAtMillis = 1L,
                voice = VoiceAttachment(uri = "/cache/v.mp3", durationSeconds = 4),
                pending = true,
            ),
        )
        val part = items.single().parts.single() as MessagePart.Voice
        assertEquals("转写中必须显式带过来", true, part.transcribing)
    }

    @Test
    fun userRowsAreNeverDroppedEvenWhenEmpty()
    {
        // 只吞助手空行：用户行（哪怕空）牵着发送状态/重试入口，归本地所有，不能由映射层删除
        val items = map(
            ChatMessage(id = "u1", role = ChatRole.USER, text = "", createdAtMillis = 1L),
        )
        assertEquals(1, items.size)
    }
}
