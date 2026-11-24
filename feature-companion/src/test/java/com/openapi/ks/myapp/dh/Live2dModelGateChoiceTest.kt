package com.openapi.ks.myapp.dh

import com.openapi.ks.dh.Live2dModelGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * 形象轮盘/装机闸（真机崩溃 2026-11-24 ADY-AL00）：OriginalDemo.moc3 是 Cubism 5 导出
 * （revision 5），端侧 Core 4.2.1 加载失败后 native 照常逐帧渲染 → SIGSEGV →
 * watchdog 把整个 LIVE2D 引擎隔离。可用性判定必须在进 native 之前把这种模型滤掉。
 */
class Live2dModelGateChoiceTest {

    private fun model(dir: File, name: String, revision: Int) {
        dir.mkdirs()
        File(dir, "$name.model3.json").writeText("""{"FileReferences":{"Moc":"$name.moc3"}}""")
        File(dir, "$name.moc3").writeBytes(
            byteArrayOf('M'.code.toByte(), 'O'.code.toByte(), 'C'.code.toByte(), '3'.code.toByte(),
                revision.toByte()) + ByteArray(8),
        )
    }

    @Test
    fun `暂存目录过不了版本闸时闸给原因`() {
        val root = createTempDir()
        model(File(root, "OriginalDemo"), "OriginalDemo", 5)
        val reason = Live2dModelGate.rejectReasonInDir(File(root, "OriginalDemo"))
        assertNotNull(reason)
    }

    @Test
    fun `r4 暂存目录放行`() {
        val root = createTempDir()
        model(File(root, "OriginalDemo"), "OriginalDemo", 4)
        assertNull(Live2dModelGate.rejectReasonInDir(File(root, "OriginalDemo")))
    }

    @Test
    fun `sanitize 拒绝不在白名单的 raw`() {
        assertEquals("ATRI", Live2dModels.sanitize("whatever"))
    }

    @Test
    fun `next 循环只在传入的 present 列表里走（闸过滤后 v5 不在场就切不到）`() {
        val present = listOf("ATRI", "Yuuka")
        assertEquals("Yuuka", Live2dModels.next("ATRI", present))
        assertEquals("ATRI", Live2dModels.next("Yuuka", present))
    }
}
