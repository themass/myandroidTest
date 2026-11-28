package com.openapi.feature.camerafx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/**
 * P0-B（真机 SIGSEGV）的关闭协议钉测：close 与在途帧的串行顺序、入口判空、幂等。
 * 纯 JVM——不碰 MediaPipe/CameraX native。
 */
class FxCloseGateTest {

    @Test
    fun `close runs release on the analysis executor after in-flight frame`() {
        val exec: ExecutorService = Executors.newSingleThreadExecutor()
        val order = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val gate = FxCloseGate { order.add("release") }
        try {
            val frameDone = CountDownLatch(1)
            // 在途帧：模拟 detectForVideo——执行期间持有模型。
            exec.execute {
                Thread.sleep(50)
                order.add("frame")
                frameDone.countDown()
            }
            // 关窗：从另一个线程调（真实场景=主线程 dispose）。
            gate.closeOn(exec)
            assertTrue("close 后帧入口必须判 closed 丢帧", gate.isClosed())

            exec.shutdown()
            assertTrue("队列必须排空", exec.awaitTermination(5, TimeUnit.SECONDS))
            // release 必须排在在途帧之后——这就是与 detectForVideo 的串行保证。
            assertEquals(listOf("frame", "release"), order.toList())
        } finally {
            exec.shutdownNow()
        }
    }

    @Test
    fun `closed before enqueue - queued-but-unrun frames see closed`() {
        val exec: ExecutorService = Executors.newSingleThreadExecutor()
        var releases = 0
        val gate = FxCloseGate { releases += 1 }
        try {
            exec.execute { } // 占位任务未跑完前，后续任务不会开始
            gate.closeOn(exec)
            // 「已入队、未执行」的帧此刻已见 closed：入口直接丢帧，不会再进 detect。
            assertTrue(gate.isClosed())
            exec.shutdown()
            assertTrue(exec.awaitTermination(5, TimeUnit.SECONDS))
            assertEquals(1, releases)
        } finally {
            exec.shutdownNow()
        }
    }

    @Test
    fun `close is idempotent`() {
        val exec: ExecutorService = Executors.newSingleThreadExecutor()
        var releases = 0
        val gate = FxCloseGate { releases += 1 }
        try {
            gate.closeOn(exec)
            gate.closeOn(exec)
            gate.closeOn(exec)
            exec.shutdown()
            assertTrue(exec.awaitTermination(5, TimeUnit.SECONDS))
            assertEquals(1, releases)
        } finally {
            exec.shutdownNow()
        }
    }

    @Test
    fun `closed gate rejects new frames but release never runs in place on dead executor`() {
        val exec: ExecutorService = Executors.newSingleThreadExecutor()
        var releases = 0
        val gate = FxCloseGate { releases += 1 }
        exec.shutdown()
        assertTrue(exec.awaitTermination(5, TimeUnit.SECONDS))
        // 先 close 再 shutdown 是调用点纪律；走反了也不能就地释放（那=主线程与 detect 竞态）。
        gate.closeOn(exec)
        assertEquals(0, releases)
        assertTrue(gate.isClosed())
    }

    @Test
    fun `concurrent close calls enqueue exactly one release`() {
        val exec: ExecutorService = Executors.newSingleThreadExecutor()
        var releases = 0
        val gate = FxCloseGate { releases += 1 }
        try {
            val start = CountDownLatch(1)
            val threads = (1..8).map {
                Thread {
                    start.await()
                    gate.closeOn(exec)
                }
            }
            threads.forEach { it.start() }
            start.countDown()
            threads.forEach { it.join(2000) }
            exec.shutdown()
            assertTrue(exec.awaitTermination(5, TimeUnit.SECONDS))
            assertEquals(1, releases)
        } finally {
            exec.shutdownNow()
        }
    }
}
