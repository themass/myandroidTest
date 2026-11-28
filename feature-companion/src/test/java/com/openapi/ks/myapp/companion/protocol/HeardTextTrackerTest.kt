package com.openapi.ks.myapp.companion.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 字幕流的端侧镜像（C2 的数据源）：`lk.transcription` 的 delta 逐块攒起来，
 * 打断时 [snapshot] 就是「这条回复送到端上的全部文本」，`HeardPrefix.slice`
 * 再按播放器进度切出前缀。回合边界（TurnDone）清零，不留上一轮的字。
 */
class HeardTextTrackerTest {

    @Test
    fun `deltas accumulate in order`() {
        val t = HeardTextTracker()
        t.onDelta("你好")
        t.onDelta("，我是")
        t.onDelta("小薇")
        assertEquals("你好，我是小薇", t.snapshot())
    }

    @Test
    fun `turn end clears the mirror`() {
        val t = HeardTextTracker()
        t.onDelta("上一轮")
        t.onTurnDone()
        assertEquals("", t.snapshot())
    }

    @Test
    fun `snapshot after interrupt keeps the interrupted turn out`() {
        // 打断后 ViewModel 截断的是气泡；传输层的镜像同步清掉，
        // 否则下一条 barge 会拿上一轮的全文去切前缀。
        val t = HeardTextTracker()
        t.onDelta("第一轮的话")
        t.onInterrupted()
        assertEquals("", t.snapshot())
    }

    @Test
    fun `tracker caps its memory`() {
        // 一轮回复最长也就几千字；镜像只为切前缀服务，超预算的尾巴服务端会按
        // est_ms 口径兜底（见契约文档），端上不无限攒。
        val t = HeardTextTracker(maxChars = 10)
        repeat(5) { t.onDelta("0123456789") }
        assertEquals(10, t.snapshot().length)
    }
}
