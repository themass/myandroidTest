package com.openapi.feature.chat.presentation.ui

import com.openapi.chatkit.model.MessagePart
import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.ImageAttachment
import com.openapi.feature.chat.domain.model.ImageSource
import com.openapi.speech.audio.AudioPlayback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 断网/弱网进页的前提（整改清单 P1-10）：签名换不到 URL 的远程图，`path` 是空串，
 * 但这一格**必须照常进气泡**——组件才有地方画「图片加载失败，点击重试」占位。
 *
 * 如果有人为了「少画一个空块」在这里把空 path 过滤掉，失败占位就整条链路消失了：
 * 用户看到的是「这条消息没有图」，也没有重试入口。所以钉在这里，而不是只写在注释里。
 */
class ChatItemMapperImagesTest {

    private fun map(vararg images: ImageAttachment): MessagePart.Images? =
        ChatItemMapper.map(
            messages = listOf(
                ChatMessage(id = "m1", role = ChatRole.USER, text = "", createdAtMillis = 1L, images = images.toList()),
            ),
            playback = AudioPlayback(),
            speakingId = null,
            likedIds = emptySet(),
            dislikedIds = emptySet(),
        ).single().parts.filterIsInstance<MessagePart.Images>().singleOrNull()

    @Test
    fun anUnresolvableRemoteImageStillProducesAnImageSlot() {
        // hydrateRemoteImages 失败后退化成的形状：REMOTE + path 空
        val part = map(ImageAttachment(path = "", source = ImageSource.REMOTE, uploadId = "u1"))
        assertNotNull("空 path 不能被过滤掉：过滤了就没有失败占位可画", part)
        assertEquals(listOf(""), part!!.sources)
    }

    @Test
    fun emptyAndGoodImagesKeepTheirSlotsAndOrder() {
        // 下标必须与 message.images 一一对应：点重试时 MessageAction.RetryImage(index) 靠它定位
        val part = map(
            ImageAttachment(path = "/data/local/a.jpg", source = ImageSource.FILE),
            ImageAttachment(path = "", source = ImageSource.REMOTE, uploadId = "u2"),
            ImageAttachment(path = "https://signed/u3", source = ImageSource.REMOTE, uploadId = "u3"),
        )
        assertEquals(
            listOf("/data/local/a.jpg", "", "https://signed/u3"),
            part?.sources,
        )
    }

    @Test
    fun aMessageWithoutImagesHasNoImagePart() {
        val part = map()
        assertEquals("没图就不该凭空造一个图片块", null, part)
    }
}
