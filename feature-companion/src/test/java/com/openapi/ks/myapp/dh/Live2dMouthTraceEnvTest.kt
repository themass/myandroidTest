package com.openapi.ks.myapp.dh

import com.openapi.ks.myapp.avatar.AvatarCommand
import com.openapi.ks.myapp.avatar.AvatarRegion
import com.openapi.ks.myapp.avatar.AvatarTouch
import com.openapi.ks.myapp.avatar.AvatarTouchKind
import com.openapi.ks.myapp.avatar.TouchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * 假电平源驱动的**包络对齐**测试（mock harness 的 JVM 侧硬判据）：
 * 按 envclip 的包络（1s 静默 →(0.4s 开/0.2s 合)×5→ 1s 静默）以 20ms 节拍喂
 * [Live2dDigitalHumanEngine.dispatch]，用假时钟 + ShadowLog 抓 `Live2dTouch: mouth v=… t=…`
 * 轨迹，断言它与包络逐段对齐：
 * - 首段静默不产生记录；每个开口段内只有 >0 的值；每段末尾补一条收口 0；
 * - 尾段静默零记录。
 * 真机上同一条判据拿真值-时间对核对——测试里钉的是这条证据通道本身可信。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Live2dMouthTraceEnvTest {

    /** envclip 包络的离散版：t 毫秒的嘴部电平（开口段取注入后实测约 0.48 的量级）。纯整数运算——浮点比较在边界帧会漂。 */
    private fun envelope(tMs: Long): Float {
        if (tMs < 1000L || tMs >= 4000L) return 0f
        val seg = ((tMs - 1000L) % 600L) / 100L // 0.6s 周期：前 4 格开、后 2 格合
        return if (seg < 4) 0.48f else 0f
    }

    /** 假时钟喂一遍包络，返回日志里的 (t, v) 序列。 */
    private fun runEnvelope(): Pair<Live2dDigitalHumanEngine, List<Pair<Long, Float>>> {
        ShadowLog.clear()
        val engine = Live2dDigitalHumanEngine()
        var fake = 0L
        engine.clockMs = { fake }
        var t = 0L
        while (t <= 5000L) {
            engine.dispatch(AvatarCommand.Mouth(envelope(t)))
            t += 20L
            fake = t
        }
        val entries = ShadowLog.getLogsForTag("Live2dTouch")
            .map { it.msg }
            .filter { it.startsWith("mouth ") }
            .map { it.substringAfter("v=").substringBefore(" ").toFloat() to it.substringAfter("t=").toLong() }
            .map { it.second to it.first }
        return engine to entries
    }

    @Test
    fun `envclip 包络的嘴型轨迹逐段对齐——静默无记录、开口段非零、收口补零`() {
        val (engine, entries) = runEnvelope()
        try {
            assertTrue("轨迹不能为空", entries.isNotEmpty())
            // 首段 1s 静默：第一条记录必须落在开口开始后
            assertTrue("首条落在首开口段 ${entries.first()}", entries.first().first in 1000..1020)
            // 每个开口段：段内有 >0.4 的记录、段内（不含收口帧）不混 0
            for (k in 0 until 5) {
                val start = 1000 + k * 600
                val seg = entries.filter { it.first in start until (start + 400) }
                assertTrue("第 ${k + 1} 开口段有记录", seg.isNotEmpty())
                assertTrue("第 ${k + 1} 开口段全非零: $seg", seg.all { it.second > 0.4f })
                // 段末第一帧静默 = 收口 0
                assertEquals("第 ${k + 1} 段收口帧", 0f, entries.first { it.first == start + 400L }.second, 1e-6f)
            }
            // 尾段静默（最后一个收口帧 t=3800 之后）：零记录
            assertTrue("尾静默无记录", entries.none { it.first > 3800L })
            // 节流上限：5s 里 ≤30 条（4 采样 + 1 收口 每周期 ×5 = 25）
            assertTrue("≤10Hz 节流 ${entries.size}", entries.size in 20..30)
        } finally { engine.release() }
    }

    @Test
    fun `口型轨迹与触点表情共存——嘴动期间点头仍走状态机`() {
        ShadowLog.clear()
        val engine = Live2dDigitalHumanEngine()
        var fake = 0L
        engine.clockMs = { fake }
        // 嘴在动的同时点一次头：两条证据通道都必须存在，互不吞。
        engine.dispatch(AvatarCommand.Mouth(0.4f))
        val tap = engine.onTouch(AvatarTouch(0.5f, 0.2f, AvatarTouchKind.TAP))
        engine.dispatch(AvatarCommand.Mouth(0f))
        engine.dispatch(AvatarCommand.Emotion("blush"))
        try {
            assertEquals(TouchResult.Handled(AvatarRegion.HEAD), tap)
            val msgs = ShadowLog.getLogsForTag("Live2dTouch").map { it.msg }
            assertTrue("嘴型记录在", msgs.any { it.startsWith("mouth v=0.400") })
            assertTrue("触点记录在", msgs.any { it.startsWith("tap model=ATRI region=${AvatarRegion.HEAD}") })
        } finally { engine.release() }
    }
}
