package com.openapi.ks.myapp.companion.protocol

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「已精确播出的文本前缀」（超越 OLLMV 第二刀 C2）。
 *
 * 端侧播放器进度是精确的：`SpeechTimeline.spokenChars` 给出「说出口了几个字」，
 * 这一层把那个字数切到**字幕文本**上，产出随 `control.barge_in` 回传的 `heard_text`。
 * OLLMV 回传的是前端猜的全量文本（`interrupt-signal {text: fullResponse}`），
 * 我方回传的是播放器证据切出的前缀——服务端 memory 手术（heard + 「[Interrupted by user]」）
 * 吃的是这个数，切错方向就是让下一轮模型以为她说了没说的话。
 *
 * 契约定在 `docs/design/2026-11-28-heard-prefix-feedback-contract.md`。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class HeardPrefixTest {

    // ------------------------------------------------------------ 切分

    @Test
    fun `slice cuts the subtitle text at the spoken character count`() {
        assertEquals("一二三四", HeardPrefix.slice("一二三四五六七八", 4))
    }

    @Test
    fun `slice never invents characters the subtitle stream has not delivered`() {
        // delta 流落后于音频：spokenChars 比气泡长，取全量，不编字。
        assertEquals("很短", HeardPrefix.slice("很短", 40))
    }

    @Test
    fun `slice of nothing spoken is empty`() {
        assertEquals("", HeardPrefix.slice("还没出声", 0))
        assertEquals("", HeardPrefix.slice("还没出声", -5))
    }

    @Test
    fun `slice counts code points, not utf-16 units`() {
        // 服务端 `chars` 是 Python len() —— 码点。端侧切分必须同口径：
        // 按 UTF-16 切会把 emoji 劈成半个代理对，服务端拿到的就是坏文本。
        val text = "你好👍世界"
        assertEquals("你好👍", HeardPrefix.slice(text, 3))
        assertEquals("你好👍世", HeardPrefix.slice(text, 4))
    }

    // ------------------------------------------------------------ 预算与禁静默

    @Test
    fun `barge args carry speech_id played_ms reason and heard_text`() {
        val args = HeardPrefix.bargeArgs(
            speechId = "sp_1",
            playedMs = 1_234L,
            reason = "hand",
            streamedText = "一二三四五六七八",
            spokenChars = 4,
        )
        assertEquals("sp_1", args.getString("speech_id"))
        assertEquals(1_234L, args.getLong("played_ms"))
        assertEquals("hand", args.getString("reason"))
        assertEquals("一二三四", args.getString("heard_text"))
        assertFalse(args.optBoolean("heard_truncated", false))
    }

    /**
     * 禁「静默无效」（C2 契约测试红线）：heard_text 再长也不能把整条 barge 撑爆。
     * 超预算时**只裁 heard_text**，speech_id / played_ms / reason 必须原样在——
     * 打断本身比前缀更重要，前缀降级成截断版而不是整条消失。
     */
    @Test
    fun `a heard_text over budget shrinks instead of dropping the cancel`() {
        val long = "很长的字".repeat(800) // 3200 码点 ≈ 9.6 KB UTF-8
        val args = HeardPrefix.bargeArgs(
            speechId = "sp_2",
            playedMs = 9_000L,
            reason = "vad",
            streamedText = long,
            spokenChars = long.length,
        )
        val heard = args.getString("heard_text")
        assertTrue("heard_text 必须压进预算", HeardPrefix.utf8Len(heard) <= HeardPrefix.MAX_HEARD_BYTES)
        assertTrue("裁过要留痕", args.getBoolean("heard_truncated"))
        // 骨架字段一个不少：前缀降级，打断不降级。
        assertEquals("sp_2", args.getString("speech_id"))
        assertEquals(9_000L, args.getLong("played_ms"))
        assertEquals("vad", args.getString("reason"))
        // 裁的是尾巴，前缀本身保持是前缀。
        assertTrue(long.startsWith(heard))
    }

    @Test
    fun `empty heard text is still sent explicitly`() {
        // 一个字都没说出口也要显式带 heard_text:""：服务端据此区分
        // 「这个端会回前缀（空=真没播出）」与「这个端根本不支持（键缺失）」。
        val args = HeardPrefix.bargeArgs("sp_3", 0L, "hand", "", 0)
        assertEquals("", args.getString("heard_text"))
    }
}
