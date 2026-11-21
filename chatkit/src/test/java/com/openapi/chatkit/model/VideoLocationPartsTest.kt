package com.openapi.chatkit.model

import com.openapi.chatkit.message.parts.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P2-13：视频/位置进消息列表后，ChatItem 必须原样携带新部件（渲染顺序固定），
 * formatDuration 的边界值（正好进位、跨小时、负数）钉死在这里——气泡上没有别的
 * 地方能暴露它们错。
 */
class VideoLocationPartsTest {

    @Test
    fun formatDurationRendersMinuteAndHourShapes() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:07", formatDuration(7))
        assertEquals("1:05", formatDuration(65))
        assertEquals("59:59", formatDuration(3599))
        // 跨小时：分钟补零到两位
        assertEquals("1:00:00", formatDuration(3600))
        assertEquals("2:03:04", formatDuration(7384))
        // 负数（读元数据失败的兜底输入）不能显示成 -1:00
        assertEquals("0:00", formatDuration(-5))
    }

    @Test
    fun videoAndLocationPartsSurviveChatItemPlainTextRules() {
        val item = ChatItem(
            id = "1",
            sender = Sender.User,
            parts = listOf(
                MessagePart.Video(name = "a.mp4", durationSeconds = 65, thumbnailPath = "/tmp/thumb.jpg"),
                MessagePart.Location(31.23, 121.47, 120),
                MessagePart.Text("看这个"),
            ),
        )
        // 视频/位置不产生文字，plainText 只收 Text：不能被新部件污染
        assertEquals("看这个", item.plainText)
    }
}
