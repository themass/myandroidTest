package com.openapi.ks.myapp.companion.protocol

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `spokenChars`：打断瞬间「实际说出口了多少个字」（P2 契约
 * docs/design/2026-11-24-state-cancel-contract-v2.md §2.1-2）。
 *
 * 字幕截断与历史入库都吃这个数，所以它必须钉死：完整段照收 `chars`，正在播的段按
 * `est_ms` 线性比例取字符，播过头封顶。`est_ms` 是估计值——这里要的不是精确，而是
 * 「不把没说的话留在气泡里」，宁可少留一个字。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SpeechTimelineSpokenCharsTest {

    private fun timeline(vararg estMs: Long): SpeechTimeline {
        val tl = SpeechTimeline("sp_1")
        estMs.forEachIndexed { idx, est ->
            tl.append(
                SpeechTimeline.parseSeg(
                    JSONObject()
                        .put("speech_id", "sp_1")
                        .put("idx", idx)
                        .put("chars", 10)
                        .put("est_ms", est),
                )!!.second,
            )
        }
        return tl
    }

    @Test
    fun `段中打断按比例取字符`() {
        val tl = timeline(1000, 1000, 1000)
        assertEquals(0, tl.spokenChars(0))
        assertEquals(5, tl.spokenChars(500))
        assertEquals(15, tl.spokenChars(1500))
    }

    @Test
    fun `段边界归上一段说满`() {
        val tl = timeline(1000, 1000)
        assertEquals(10, tl.spokenChars(1000))
        assertEquals(20, tl.spokenChars(2000))
    }

    @Test
    fun `播过头封顶为全部 chars`() {
        val tl = timeline(1000, 1000)
        assertEquals(20, tl.spokenChars(9000))
    }

    @Test
    fun `est_ms 为 0 的段整段计入而不除零`() {
        val tl = timeline(1000, 0, 1000)
        // 第二段有效时长 0：它「说完了」的瞬间就是第一段结束，chars 整段归入。
        assertEquals(20, tl.spokenChars(1000))
        assertEquals(30, tl.spokenChars(2000))
    }
}
