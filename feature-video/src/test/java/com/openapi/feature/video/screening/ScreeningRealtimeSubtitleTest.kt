package com.openapi.feature.video.screening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 实时字幕接线层的 JVM 单测：地址判定（哪些片源能重解）与 seek 判定（什么时候必须重连识别）。
 * 这两条是全功能仅有的纯逻辑，引擎内部走的是真播放器，JVM 测不了。
 */
class ScreeningRealtimeSubtitleTest {

    // ---------- 地址判定 ----------

    @Test
    fun `在线地址原样用作重解地址`() {
        assertEquals(
            "https://cdn.test/a.m3u8",
            realtimeMediaUrl("https://cdn.test/a.m3u8", "https", null),
        )
        assertEquals("http://a.test/x.mp4", realtimeMediaUrl("http://a.test/x.mp4", "http", null))
    }

    @Test
    fun `file 地址去掉 scheme 变成路径`() {
        assertEquals("/sdcard/x.mp4", realtimeMediaUrl("file:///sdcard/x.mp4", "file", null))
        assertEquals("/data/x.mp4", realtimeMediaUrl("/data/x.mp4", null, null))
    }

    @Test
    fun `样片优先用落盘缓存路径`() {
        // asset:// 播放内核认识、提取器不认识；缓存文件路径两边都认
        assertEquals(
            "/data/cache/screening-reel-1.mp4",
            realtimeMediaUrl("asset:///reel_1.mp4", "asset", "/data/cache/screening-reel-1.mp4"),
        )
    }

    @Test
    fun `手机里挑的文件不能重解，如实返回 null`() {
        assertNull(realtimeMediaUrl("content://media/external/video/media/42", "content", null))
        assertNull(realtimeMediaUrl(null, null, null))
        assertNull(realtimeMediaUrl("", "", null))
    }

    @Test
    fun `空白缓存路径不影响在线地址判定`() {
        // 样片还没落盘时 cacheFile 是空串，也要能退回在线地址
        assertEquals("https://a.test", realtimeMediaUrl("https://a.test", "https", "  "))
    }

    // ---------- seek 判定 ----------

    private fun jumped(posFrom: Long, posTo: Long, elapsedAdvanceMs: Long, speed: Float, playing: Boolean = true) =
        realtimeSeekJumped(posFrom, posTo, 0L, elapsedAdvanceMs, speed, playing)

    @Test
    fun `正常播放不判跳转`() {
        // 0.5 秒墙钟、1× 速：进度推进约 500ms，远小于阈值
        assertFalse(jumped(10_000, 10_500, 500, 1f))
    }

    @Test
    fun `进度条拖动要判跳转`() {
        // 0.5 秒墙钟里进度跳了 70 秒
        assertTrue(jumped(10_000, 80_000, 500, 1f))
    }

    @Test
    fun `暂停期间永不判跳转`() {
        // 位置冻住但墙钟在走：不按住这条，每次暂停都会误重启
        assertFalse(jumped(10_000, 10_000, 30_000, 1f, playing = false))
    }

    @Test
    fun `倍速下要按倍率折算墙钟`() {
        // 2× 速、0.5 秒墙钟 = 正常推进 1000ms，不该判
        assertFalse(jumped(10_000, 11_000, 500, 2f))
        // 同样条件下进度只走了 100ms：差 900ms 仍小于 4s 阈值，也不判
        assertFalse(jumped(10_000, 10_100, 500, 2f))
    }

    @Test
    fun `阈值边界内不算跳转`() {
        // 推进量与期望差 4000ms = 阈值本身，不超过
        assertFalse(jumped(0, 4_500, 500, 1f))
        // 差 4001ms 就翻脸
        assertTrue(jumped(0, 4_501, 500, 1f))
    }
}
