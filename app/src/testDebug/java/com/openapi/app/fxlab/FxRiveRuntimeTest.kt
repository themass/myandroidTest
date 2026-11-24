package com.openapi.app.fxlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FxRiveRuntimeTest {

    // 真机暴露：SDK 的 androidx.startup 自动初始化被 app 的 tools:node="remove" 摘掉，
    // Rive.init 从未跑过 → 构造 RiveAnimationView 时报 FileAssetLoader 的 JNI 注册缺失。
    // Lab 自己兜底：构造前 ensure，且只 init 一次。
    @Test
    fun ensureInitializesExactlyOnce() {
        var calls = 0
        val gate = RiveRuntimeGate()
        gate.ensure { calls++ }
        gate.ensure { calls++ }
        assertEquals(1, calls)
    }

    @Test
    fun initializerFailureIsRecordedNotThrown() {
        val gate = RiveRuntimeGate()
        gate.ensure { throw IllegalStateException("boom") }
        assertTrue((gate.failure ?: "").startsWith("boom"))
    }

    @Test
    fun freshGateHasNoFailureYet() {
        assertNull(RiveRuntimeGate().failure)
    }
}
