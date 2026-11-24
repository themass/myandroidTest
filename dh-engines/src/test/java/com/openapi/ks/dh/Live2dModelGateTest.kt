package com.openapi.ks.dh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * moc3 版本闸（真机崩溃 2026-11-24 23:44/23:49 ADY-AL00）：
 * 设备端 Cubism Core 是 4.2.1，`csmReviveMocInPlace` 明说只支持到 moc3 revision 4；
 * psd2live 导出的 OriginalDemo.moc3 是 revision 5，native 建模型失败后**不返回**，
 * 逐帧 `nativeOnDrawFrame` 空指针 SIGSEGV。Kotlin 侧唯一能在加载前拦住的手段就是
 * 读 model3.json 引用的那个 .moc3 的版本字节。
 */
class Live2dModelGateTest {

    private fun moc3(versionByte: Int): ByteArray =
        byteArrayOf('M'.code.toByte(), 'O'.code.toByte(), 'C'.code.toByte(), '3'.code.toByte(),
            versionByte.toByte(), 0, 0, 0)

    private fun dir(root: File, modelDir: String, mocName: String, version: Int) {
        val d = File(root, modelDir).apply { mkdirs() }
        File(d, "$modelDir.model3.json").writeText(
            """{"Version":3,"FileReferences":{"Moc":"$mocName"}}""",
        )
        File(d, mocName).writeBytes(moc3(version))
    }

    @Test
    fun `revision 4 放行（ATRI atrri_8 同族）`() {
        val root = createTempDir()
        dir(root, "ATRI", "atri_8.moc3", 4)
        assertEquals(null, Live2dModelGate.rejectReason(root, "ATRI", "ATRI.model3.json"))
    }

    @Test
    fun `revision 3 放行（Hiyori 同族）`() {
        val root = createTempDir()
        dir(root, "Hiyori", "Hiyori.moc3", 3)
        assertEquals(null, Live2dModelGate.rejectReason(root, "Hiyori", "Hiyori.model3.json"))
    }

    @Test
    fun `revision 5 拒绝且 reason 带两侧版本号`() {
        val root = createTempDir()
        dir(root, "OriginalDemo", "OriginalDemo.moc3", 5)
        val reason = Live2dModelGate.rejectReason(root, "OriginalDemo", "OriginalDemo.model3.json")
        assertTrue("reason=$reason", reason != null && reason.contains("5") && reason.contains("4"))
    }

    @Test
    fun `model3 引用的 moc3 缺失时拒绝而不是让 native 读空`() {
        val root = createTempDir()
        val d = File(root, "Broken").apply { mkdirs() }
        File(d, "Broken.model3.json").writeText("""{"FileReferences":{"Moc":"nope.moc3"}}""")
        val reason = Live2dModelGate.rejectReason(root, "Broken", "Broken.model3.json")
        assertTrue("reason=$reason", reason != null)
    }

    @Test
    fun `magic 不是 MOC3 直接拒`() {
        val root = createTempDir()
        val d = File(root, "Junk").apply { mkdirs() }
        File(d, "Junk.model3.json").writeText("""{"FileReferences":{"Moc":"junk.moc3"}}""")
        File(d, "junk.moc3").writeText("not a moc3 at all, long enough to pass the size floor")
        val reason = Live2dModelGate.rejectReason(root, "Junk", "Junk.model3.json")
        assertTrue("reason=$reason", reason != null)
    }
}
