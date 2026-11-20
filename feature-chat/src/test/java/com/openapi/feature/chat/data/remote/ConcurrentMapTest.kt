package com.openapi.feature.chat.data.remote

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 切换会话的签名 URL 并发化（整改清单 P0-6.1）：
 * 一页历史里每张远程图片要单独换一次签名 URL（0.4–2s/张），串行时切会话
 * 的关键路径 = 图片数 × 单次时延。并发化后关键路径 ≈ 最慢的一张。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConcurrentMapTest {

    @Test
    fun slowOneIsNotSerializedAfterFastOnes() = runTest {
        val done = mutableListOf<String>()
        val result = boundedMap(listOf("a", "b", "slow"), limit = 8) { id ->
            if (id == "slow") delay(1000) else delay(10)
            done += id
            id.uppercase()
        }
        assertEquals(mapOf("a" to "A", "b" to "B", "slow" to "SLOW"), result)
        // 并发的定义：慢任务还没回来时，后面的任务已经完成——串行时 done 必然保持入参顺序
        assertTrue("慢任务必须先于完成队尾：$done", done.last() == "slow")
    }

    @Test
    fun limitIsRespected() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        var running = 0
        var maxRunning = 0
        val gate = CompletableDeferred<Unit>()
        val job = scope.async {
            boundedMap((1..10).toList(), limit = 3) {
                running++
                maxRunning = maxOf(maxRunning, running)
                gate.await()
                running--
                it
            }
        }
        scope.advanceTimeBy(1)
        assertEquals("同时在跑的任务必须不超过 limit", 3, maxRunning)
        gate.complete(Unit)
        job.await()
        assertEquals(3, maxRunning)
    }

    @Test
    fun failuresBecomeNullNotExceptions() = runTest {
        val result = boundedMap(listOf("ok", "boom")) { id ->
            if (id == "boom") throw java.io.IOException("网络断了")
            "url-$id"
        }
        assertEquals("url-ok", result["ok"])
        // 一张图拉不动不能让整页历史抛异常——和并发化之前的逐张 runCatching 语义一致
        assertEquals(null, result["boom"])
    }

    @Test
    fun emptyInputShortCircuitsWithoutCalling() = runTest {
        var calls = 0
        val result = boundedMap(emptyList<String>()) { calls++; it }
        assertEquals(emptyMap<String, String>(), result)
        assertEquals(0, calls)
    }
}
