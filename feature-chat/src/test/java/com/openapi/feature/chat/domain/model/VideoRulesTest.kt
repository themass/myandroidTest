package com.openapi.feature.chat.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P2-13 视频硬规则的边界（纯 JVM）：上限值**本身**放行、超一个字节才拒；
 * 扩展名大小写不敏感；没有扩展名就是拒。规则在两处执行（暂存 + 签发前），
 * 两处共用这里的判定，钉一处等于钉两处。
 */
class VideoRulesTest {

    @Test
    fun maxBytesIsExactlyTheContractValue() {
        // 服务端 scene chat-video 的硬上限：25 MiB
        assertEquals(26_214_400L, VideoRules.MAX_VIDEO_BYTES)
    }

    @Test
    fun sizeAtLimitPassesOneByteOverRejects() {
        assertNull(VideoRules.preflightReject("a.mp4", VideoRules.MAX_VIDEO_BYTES))
        assertEquals(VideoReject.TooLarge, VideoRules.preflightReject("a.mp4", VideoRules.MAX_VIDEO_BYTES + 1))
    }

    @Test
    fun mp4ExtensionIsCaseInsensitiveAndMustBePresent() {
        assertTrue(VideoRules.isMp4("video.mp4"))
        assertTrue(VideoRules.isMp4("video.MP4"))
        assertTrue(VideoRules.isMp4("点.多.的.Mp4"))
        assertFalse(VideoRules.isMp4("video.mov"))
        assertFalse(VideoRules.isMp4("video"))
        assertFalse(VideoRules.isMp4("mp4"))
    }

    @Test
    fun nonMp4RejectedBeforeSizeIsConsidered() {
        // 0 字节的 .mov 也先报格式错：格式比大小更根本
        assertEquals(VideoReject.NotMp4, VideoRules.preflightReject("a.mov", 0L))
    }
}
