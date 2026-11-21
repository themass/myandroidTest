package com.openapi.feature.chat.data.mapper

import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.GeoLocation
import com.openapi.feature.chat.domain.model.MessageKind
import com.openapi.feature.chat.domain.model.VideoAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P2-13 本地缓存往返：视频（含时长、uploadId）与位置写进 ChatLog.content 再读回来必须一模一样
 * ——Room 行是唯一能离线复活这两类消息的地方，往返丢一个字段，重启后就少一块显示。
 * org.json 在 JVM 是 stub，走 Robolectric。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ChatLogMapperVideoLocationTest {

    private fun roundTrip(message: ChatMessage): ChatMessage? = ChatLogMapper.toDomain(
        chatId = message.id,
        who = ChatLogMapper.toWho(message.role),
        msgType = ChatLogMapper.toMsgType(message.kind),
        rawContent = ChatLogMapper.toRawContent(message),
        createdAtMillis = message.createdAtMillis,
    )

    @Test
    fun videoMessageRoundTripsNameDurationUploadId() {
        val message = ChatMessage(
            id = "1", role = ChatRole.USER, text = "看这个", createdAtMillis = 100L,
            kind = MessageKind.VIDEO,
            videos = listOf(VideoAttachment(path = "/tmp/a.mp4", name = "a.mp4", sizeBytes = 1234L, durationSeconds = 42, uploadId = "up_1")),
        )
        val back = roundTrip(message)!!
        assertEquals(MessageKind.VIDEO, back.kind)
        assertEquals("看这个", back.text)
        val video = back.videos.single()
        assertEquals("/tmp/a.mp4", video.path)
        assertEquals("a.mp4", video.name)
        assertEquals(1234L, video.sizeBytes)
        assertEquals(42, video.durationSeconds)
        assertEquals("up_1", video.uploadId)
        // 缩略图文件不存在 → 不写也不读回来（写侧 takeIf exists，读侧同款）
        assertNull(video.thumbnailPath)
    }

    @Test
    fun remoteVideoWithoutLocalFileSurvivesAsUploadIdOnly() {
        val message = ChatMessage(
            id = "2", role = ChatRole.ASSISTANT, text = "", createdAtMillis = 100L,
            kind = MessageKind.VIDEO,
            videos = listOf(VideoAttachment(path = "", name = "a.mp4", durationSeconds = 7, uploadId = "up_2")),
        )
        val back = roundTrip(message)!!
        val video = back.videos.single()
        assertEquals("", video.path)
        assertEquals("up_2", video.uploadId)
        assertEquals(7, video.durationSeconds)
    }

    @Test
    fun locationMessageRoundTripsCoordinatesAndAccuracy() {
        val message = ChatMessage(
            id = "3", role = ChatRole.USER, text = "在这儿", createdAtMillis = 100L,
            kind = MessageKind.LOCATION,
            location = GeoLocation(31.23, 121.47, 120),
        )
        val back = roundTrip(message)!!
        assertEquals(MessageKind.LOCATION, back.kind)
        assertEquals("在这儿", back.text)
        assertEquals(GeoLocation(31.23, 121.47, 120), back.location)
    }

    @Test
    fun locationWithoutAccuracyRoundTripsAsNull() {
        val message = ChatMessage(
            id = "4", role = ChatRole.USER, text = "", createdAtMillis = 100L,
            kind = MessageKind.LOCATION,
            location = GeoLocation(31.23, 121.47),
        )
        val back = roundTrip(message)!!
        assertNotNull(back.location)
        assertNull(back.location!!.accuracyMeters)
    }

    /** msgType 是新类型编号：不能撞上 TXT/AUDIO/IMAGE/FILE 的旧值。 */
    @Test
    fun newMsgTypesDoNotCollideWithLegacyOnes() {
        val types = listOf(
            ChatLogMapper.TYPE_TXT, ChatLogMapper.TYPE_AUDIO, ChatLogMapper.TYPE_IMAGE, ChatLogMapper.TYPE_FILE,
            ChatLogMapper.TYPE_VIDEO, ChatLogMapper.TYPE_LOCATION,
        )
        assertEquals(types.size, types.toSet().size)
        assertEquals(3, ChatLogMapper.TYPE_VIDEO) // core MessageType.VIDEO_TYPE 的既有值
    }
}
