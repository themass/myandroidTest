package com.openapi.feature.camerafx

import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 关闭协议：把「释放原生模型」排进分析队列，与在途帧串行（纯 JVM 层，单测钉死）。
 *
 * 真机 P0（HUAWEI ADY-AL00，2026-11-28 验收）：composable dispose 时直接在主线程
 * `analyzer.close()`，与 analysisExecutor 上的在途帧竞态——`detectForVideo` 还在
 * native 里跑，模型句柄已被删除 → SIGSEGV
 * （`AndroidPacketCreator.nativeCreateRgbaImage` null deref，fault addr 0x160）。
 *
 * 协议（[FxAnalyzer] 与 [CameraFxGrantedContent] 共同遵守）：
 * 1. [closeOn] **一被调用就置 closed**（在入队之前）：此后投递的帧在入口直接丢弃，
 *    不再进 detect——「已入队、未执行」窗口里的帧也不会再碰模型。
 * 2. 释放动作排入分析执行器：单线程队列保证它排在所有已接受的在途帧之后，
 *    绝不与 `detectForVideo` 交错。
 * 3. 幂等：重复 [closeOn] 只释放一次。executor 已 shutdown 时不就地释放
 *    （那正是本类要修的竞态）——宁可不释放（泄漏模型句柄），不可崩进程。
 *    调用方必须先 [closeOn] 再 `shutdown()`。
 */
class FxCloseGate(private val release: () -> Unit) {

    private val closed = AtomicBoolean(false)

    /** 帧入口判定：true = 关闭已开始，直接丢帧（`image.close()` 由调用方负责）。 */
    fun isClosed(): Boolean = closed.get()

    fun closeOn(executor: Executor) {
        if (!closed.compareAndSet(false, true)) return
        try {
            executor.execute(release)
        } catch (_: RejectedExecutionException) {
            // 已 shutdown：队列里可能仍有在途帧，就地释放=主线程与 detect 竞态，
            // 不能做。泄漏 native 句柄可接受，崩进程不可接受。调用点保证走不到这里。
        }
    }
}
