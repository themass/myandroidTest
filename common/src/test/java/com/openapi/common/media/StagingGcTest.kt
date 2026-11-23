package com.openapi.common.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 暂存孤儿文件的启动清扫（cleanup 项 5）。
 *
 * 钉三件事：
 * 1. 超过 [StagingGc.MAX_AGE_MILLIS] 的文件被删；
 * 2. **没到龄的一律不碰**——这条是「按年龄」而不是「按上次启动」的全部意义：陪伴页正在
 *    逐张上传的那些文件年龄只有几秒，误删就是把一次正常的发送打成「上传失败」；
 * 3. 目录不存在/读不到 → 0，不抛：GC 永远不该把一次选图变成错误。
 */
class StagingGcTest {

    private val now = 1_700_000_000_000L

    private fun tempDir(): File = File(System.getProperty("java.io.tmpdir"), "staging-gc-${System.nanoTime()}").apply { mkdirs() }

    private fun File.touchAt(millis: Long) {
        assertTrue("设置 mtime 失败", setLastModified(millis))
    }

    @Test
    fun `files older than the retention window are deleted, fresh ones survive`() {
        val dir = tempDir()
        val old = File(dir, "img_old.jpg").apply { writeText("x"); touchAt(now - StagingGc.MAX_AGE_MILLIS - 60_000) }
        val fresh = File(dir, "img_fresh.jpg").apply { writeText("x"); touchAt(now - 60_000) }

        assertEquals(1, StagingGc.sweep(dir, now))
        assertFalse("超龄孤儿必须走", old.exists())
        assertTrue("刚写的暂存文件不能碰（陪伴页可能正在逐张上传）", fresh.exists())
    }

    @Test
    fun `subdirectories are left alone`() {
        val dir = tempDir()
        val sub = File(dir, "not-a-file").apply { mkdirs() }
        sub.setLastModified(now - StagingGc.MAX_AGE_MILLIS - 60_000)

        assertEquals(0, StagingGc.sweep(dir, now))
        assertTrue("只删文件，不递归删目录", sub.exists())
    }

    @Test
    fun `a missing or unreadable directory sweeps nothing and never throws`() {
        assertEquals(0, StagingGc.sweep(File("/nonexistent/staging-${System.nanoTime()}"), now))
    }
}
