package com.openapi.ks.myapp.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors

/**
 * 暂存时间戳的**并发**契约（复审 minor 1）。
 *
 * `onImagesPicked` 的时间戳决定暂存文件名（`img_<stamp>.jpg`），KDoc 早就写着
 * 「必须是单调递增的」，但实现是一句普通的读-改-写：
 * `lastStagingStamp = if (now > lastStagingStamp) now else lastStagingStamp + 1`。
 * 而取时间戳的位置在 `withContext(ioDispatcher)` **里面**——相册多选与拍照两条路可以同时
 * 在跑，两个协程落在 IO 的不同线程上，同一毫秒内各自读到同一个 `lastStagingStamp`，
 * 写出同一个 stamp → 后一张**静默覆盖**前一张（`MediaUploader` 按名字写文件，没有任何报错），
 * 用户看到「选了九张只剩一张」，正是那条注释想防的事，注释却与实现不符。
 *
 * 这里用一道自旋栅栏把线程挤进同一毫秒，钉「拿到的 stamp 一个都不重」。
 * 非原子的读-改-写在栅栏下必然撞车（跑一次就红），CAS 版本必然过。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompanionStagingStampTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        CompanionDependencies.installStrings(FakeCompanionStrings())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(): CompanionViewModel = CompanionViewModel(
        api = null,
        transportFactory = { FakeCompanionTransport(reply = "") },
        inboxPollMillis = 60_000,
    )

    @Test
    fun `stamps taken from several threads never repeat`() {
        // 量级要够：一次「相册多选 + 拍照」只有两次调用，但撞不撞取决于落点。
        // 这里让 16 个线程在同一毫秒里各取 500 个 stamp——读-改-写的版本必然丢一堆
        // （实测几百个重复），原子版本一个不丢。
        val threads = 16
        val perThread = 500
        val model = vm()
        val pool = Executors.newFixedThreadPool(threads)
        val barrier = CyclicBarrier(threads)
        val got = java.util.concurrent.ConcurrentLinkedQueue<Long>()
        try {
            val futures = (1..threads).map {
                pool.submit {
                    barrier.await()
                    repeat(perThread) { got += model.nextStagingStamp() }
                }
            }
            futures.forEach { it.get(10, java.util.concurrent.TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
            model.stopListening()
        }

        val all = got.toList()
        assertEquals(
            "取了多少次就该有多少个不同的名字（重复 = 同名文件互相覆盖，用户少一张图）",
            threads * perThread, all.distinct().size,
        )
        assertTrue("每个 stamp 都得是真实时钟量级", all.all { it > 1_000_000_000_000L })
    }

    @Test
    fun `single-threaded stamps stay strictly increasing`() {
        val model = vm()
        try {
            val stamps = List(5) { model.nextStagingStamp() }
            assertEquals("单线程里也不能重复（同一毫秒多选九张）", stamps.size, stamps.distinct().size)
            stamps.zipWithNext().forEach { (a, b) -> assertTrue("必须递增：$a -> $b", b > a) }
        } finally {
            model.stopListening()
        }
    }
}
