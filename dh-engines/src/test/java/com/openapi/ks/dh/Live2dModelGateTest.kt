package com.openapi.ks.dh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * moc3 版本闸（真机崩溃 2026-11-24 23:44/23:49 ADY-AL00）。
 *
 * 当年设备端 Core 是 4.2.1，`csmReviveMocInPlace` 只支持到 moc3 revision 4，
 * psd2live 导出的 OriginalDemo.moc3 是 revision 5 → native 建模型失败后**不返回**，
 * 逐帧 `nativeOnDrawFrame` 空指针 SIGSEGV。Kotlin 侧唯一能在加载前拦住的手段就是
 * 读 model3.json 引用的那个 .moc3 的版本字节。
 *
 * 2026-11-25 端侧 Core 升到 5-r.5（`csmMocVersion_53 = 6`），上限随之抬到 6；
 * r5（psd2live 3.8.4 导出）从此放行，比 6 新的仍然拦。
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
    fun `revision 5 放行（Core 5r5 起 psd2live 导出可用）`() {
        val root = createTempDir()
        dir(root, "OriginalDemo", "OriginalDemo.moc3", 5)
        assertEquals(null, Live2dModelGate.rejectReason(root, "OriginalDemo", "OriginalDemo.model3.json"))
    }

    @Test
    fun `revision 6 放行（Core 5r5 声明 csmMocVersion_53=6）`() {
        val root = createTempDir()
        dir(root, "Cubism53Demo", "c53.moc3", 6)
        assertEquals(null, Live2dModelGate.rejectReason(root, "Cubism53Demo", "Cubism53Demo.model3.json"))
    }

    @Test
    fun `revision 7 拒绝且 reason 带两侧版本号（超出 Core 5r5 上限）`() {
        val root = createTempDir()
        dir(root, "FutureDemo", "FutureDemo.moc3", 7)
        val reason = Live2dModelGate.rejectReason(root, "FutureDemo", "FutureDemo.model3.json")
        assertTrue("reason=$reason", reason != null && reason.contains("7") && reason.contains("6"))
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
