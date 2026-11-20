package com.openapi.ks.myapp.dh

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Duix 引擎的 PCM 泵接线回归：[DuixDigitalHumanEngine.pushRemotePcm] 必须过闸门泵——
 * 静默块永远不推、也不提前 flush（静默态头部小幅摆动 bug 的回归钉），说完一段只 flush
 * 一次收尾。时钟与泵经构造注入（internal 构造 + pumpFactory），不碰 SystemClock / runtime。
 */
class DuixEnginePcmPumpTest {

    /** 20ms @ 16k mono 的全零块。 */
    private val silent = ByteArray(640) { 0 }

    /** 头两字节 0x00 0x40 → int16 16384，峰值 0.5 ≥ 0.02 门限，有声。 */
    private val voiced = ByteArray(640).apply {
        this[0] = 0x00
        this[1] = 0x40
    }

    /** 计数泵替身：只观察「引擎是否经泵推送 / flush」，不需要 runtime。 */
    private class Harness {
        val pushes = mutableListOf<ByteArray>()
        var flushes = 0

        val engine = DuixDigitalHumanEngine(
            modelName = "test-model",
            nowMs = { nowMs },
            // 忽略引擎注入的真实 push/flush（那要 runtime），换成计数端点；
            // 泵本身仍是真的 RemotePcmPump + PcmGate，闸门语义原样参与。
            pumpFactory = { clock, _, _ ->
                RemotePcmPump(nowMs = clock, push = { pushes += it }, flush = { flushes += 1 })
            },
        )

        var nowMs = 0L

        fun feedAt(pcm: ByteArray, atMs: Long) {
            nowMs = atMs
            engine.pushRemotePcm(pcm)
        }
    }

    @Test
    fun `continuous silence never reaches the runtime and never flushes`() {
        val h = Harness()
        // LiveKit 静默时持续推近零块：全程不推，也不许提前 flush。
        repeat(5) { i -> h.feedAt(silent, atMs = i * 200L) }
        assertEquals(0, h.pushes.size)
        assertEquals(0, h.flushes)
    }

    @Test
    fun `voiced burst pushes then exactly one flush closes the segment`() {
        val h = Harness()
        repeat(3) { i -> h.feedAt(voiced, atMs = i * 10L) } // 0/10/20ms 有声，全推
        h.feedAt(silent, atMs = 100L) // 距最后有声 80ms < 400ms 余量 → 仍推（尾音不掐）
        h.feedAt(silent, atMs = 300L) // 280ms < 余量 → 仍推
        h.feedAt(silent, atMs = 500L) // 480ms ≥ 余量 → 收尾一次
        h.feedAt(silent, atMs = 700L) // 之后维持待机：不推不刷
        h.feedAt(silent, atMs = 900L)
        assertEquals(5, h.pushes.size) // 3 有声 + 2 余量内
        assertEquals(1, h.flushes)
    }

    @Test
    fun `flushRemotePcm ends the push session immediately`() {
        val h = Harness()
        h.feedAt(voiced, atMs = 0L)
        h.engine.flushRemotePcm() // rig 播完明示收尾：不等闸门量余量
        assertEquals(1, h.flushes)
        h.engine.flushRemotePcm() // 再次明示仍可刷（泵不自己拦，闸门 END 才只报一次）
        assertEquals(2, h.flushes)
    }
}
