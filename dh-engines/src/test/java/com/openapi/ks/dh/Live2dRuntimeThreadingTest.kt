package com.openapi.ks.dh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 线程纪律回归测试（真机事故 2026-11-22 18:18）。
 *
 * 通话页 Live2D 渲染中，GLThread 在 `nativeOnDrawFrame` 里逐帧更新 Cubism 场景，
 * 而 [Live2dRuntime.setExpression]/[setMouth]/[setGaze] 从调用方线程（Compose 主线程、
 * 音频协程）**直调** JNI 改同一个场景。vendored `libchatwaifu-live2d.so` 内部无锁：
 * 真机 SIGSEGV（SEGV_MAPERR, fault addr 0x14）在 GLThread 的 drawFrame 里爆出来——
 * 点一下形象几秒后整个进程没了，crash buffer 只有 native 栈没有 Java 异常。
 *
 * 规则：所有改场景的 native 调用必须调度到 GL 线程（生产 = `GLSurfaceView.queueEvent`）。
 * 测试注入 [Live2dRuntime.JniDispatcher] 记录被调度的块，验证：
 * ① 改场景的调用是「投递」而不是「就地执行」；② 未启动/已释放时不投递。
 */
class Live2dRuntimeThreadingTest {

    private class RecordingDispatcher : Live2dRuntime.JniDispatcher {
        val posted = mutableListOf<() -> Unit>()
        override fun post(block: () -> Unit) {
            posted += block
        }
    }

    private fun runtime(dispatcher: RecordingDispatcher, started: Boolean = true): Live2dRuntime =
        Live2dRuntime.forTest(
            modelsReady = true,
            nativeLoadable = true,
            cacheRoot = File("."),
            started = started,
            jniDispatcher = dispatcher,
        )

    @Test
    fun `setExpression 调度到 GL 而不是在调用线程就地执行`() {
        val d = RecordingDispatcher()
        runtime(d).setExpression("blush")
        assertEquals(1, d.posted.size)
    }

    @Test
    fun `setMouth 同样走调度`() {
        val d = RecordingDispatcher()
        runtime(d).setMouth(0.5f)
        assertEquals(1, d.posted.size)
    }

    @Test
    fun `setGaze 没有 surface 时不投递（像素坐标无从换算）`() {
        // 测试 runtime 从不经历 buildGlView，surface 恒为 null：gaze 在调用线程上
        // 需要 view 尺寸把 -1..1 换算成像素，无从换算就整个不调度（与修复前行为一致）。
        val d = RecordingDispatcher()
        runtime(d).setGaze(0.3f, 0.2f)
        assertTrue(d.posted.isEmpty())
    }

    @Test
    fun `未启动的 runtime 不投递任何 native 调用`() {
        val d = RecordingDispatcher()
        val rt = runtime(d, started = false)
        rt.setExpression("blush")
        rt.setMouth(1f)
        assertTrue(d.posted.isEmpty())
    }

    @Test
    fun `setBackground 同样走调度（glClearColor 每帧在 GL 线程读，写入不能跨线程裸调）`() {
        val d = RecordingDispatcher()
        runtime(d).setBackground(0.1f, 0.2f, 0.3f)
        assertEquals(1, d.posted.size)
    }

    @Test
    fun `changeModel 走 GL 调度而不是在调用线程直调 native`() {
        val d = RecordingDispatcher()
        runtime(d).changeModel("Amadeus", "Amadeus.model3.json")
        assertEquals(1, d.posted.size)
    }

    @Test
    fun `版本闸拒绝的模型 changeModel 不投递（切过去就是下一帧 SIGSEGV）`() {
        val d = RecordingDispatcher()
        val rt = Live2dRuntime.forTest(
            modelsReady = true,
            nativeLoadable = true,
            cacheRoot = File("."),
            started = true,
            jniDispatcher = d,
            modelGateReject = "模型版本过新：moc3 r5 > 端侧 Cubism Core r4",
        )
        rt.changeModel("OriginalDemo", "OriginalDemo.model3.json")
        assertEquals(0, d.posted.size)
    }

    @Test
    fun `release 之后不再投递`() {
        val d = RecordingDispatcher()
        val rt = runtime(d)
        rt.setExpression("base")
        val before = d.posted.size
        rt.release()
        rt.setExpression("blush")
        rt.setMouth(0.2f)
        assertEquals(before, d.posted.size)
    }
}
