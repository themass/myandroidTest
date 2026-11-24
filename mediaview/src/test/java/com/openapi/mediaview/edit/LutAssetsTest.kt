package com.openapi.mediaview.edit

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [LutAssets] 的记忆化钉子：**失败不进缓存，成功才进缓存**。
 *
 * 旧实现把 null 也记死（进程级）：一次瞬时 IO 失败就把整个进程的滤镜全灭到下次冷启动，
 * 界面上就是六格全黑、点了没反应，没有任何恢复机会。这里用注入读盘的 [LutAssets.getImpl]
 * 钉住新语义（真实 AssetManager 在单测里没有可控的失败注入点）。
 *
 * 记忆化是进程级的，所以每个用例用**互不相同的 id**，不互相抢这张全局表。
 * Robolectric 只为 [android.util.Log]（失败路径会写日志）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LutAssetsTest {

    @Test
    fun transientIoFailureIsNotMemoizedSoTheNextCallCanRecover() {
        var calls = 0
        val flaky: (String) -> String? = {
            calls++
            if (calls == 1) throw IOException("模拟瞬时读盘失败") else identityCube2
        }

        assertNull(LutAssets.getImpl("lut-io-flaky", flaky))
        assertNotNull("失败结果不许被永久记住：表能读出来后必须恢复可用", LutAssets.getImpl("lut-io-flaky", flaky))
        assertEquals("失败与恢复各读一次盘", 2, calls)
    }

    @Test
    fun unparseableContentIsNotMemoizedEither() {
        var calls = 0
        val read: (String) -> String? = {
            calls++
            // 第一次给一份不合预期的表（size 越界 → parse 返回 null），第二次给正常表
            if (calls == 1) "LUT_3D_SIZE 999\n0 0 0" else identityCube2
        }

        assertNull(LutAssets.getImpl("lut-parse-flaky", read))
        assertNotNull("解析失败同样不缓存", LutAssets.getImpl("lut-parse-flaky", read))
    }

    /** 对照：成功必须被记住——预览每拖一下滑杆都会问同一个 id，不记忆化滑杆就成反复 parse。 */
    @Test
    fun successfulParseIsMemoized() {
        var calls = 0
        val read: (String) -> String? = { calls++; identityCube2 }

        val first = LutAssets.getImpl("lut-warm", read)
        val second = LutAssets.getImpl("lut-warm", read)

        assertNotNull(first)
        assertSame("同一张表只 parse 一次", first, second)
        assertEquals(1, calls)
    }
}
