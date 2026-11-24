package com.openapi.ks.myapp.dh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.openapi.ks.dh.EngineAssets
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 形象轮盘的存在性判定必须过版本闸（真机崩溃 2026-11-24 ADY-AL00）。
 *
 * 只看目录存在会把端侧 Core 读不动的模型算进「可用形象」——轮盘切过去后
 * `changeModel` 在 native 建模型失败照常逐帧渲染 → SIGSEGV → watchdog 隔离整个
 * LIVE2D 引擎（真机证据链 2026-11-24；那时 Core 4.2.1 × r5 就是这个 case）。
 * Core 升 5-r.5 后上限是 r6，r7 以上仍是这条红线的守门对象。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Live2dPresentModelsGateTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DhLabBootstrap.resetForTests()
        DhLabBootstrap.ensureDefaults()
    }

    private fun seed(root: File, dirName: String, mocName: String, revision: Int) {
        val d = File(root, dirName).apply { mkdirs() }
        File(d, "$dirName.model3.json").writeText("""{"FileReferences":{"Moc":"$mocName"}}""")
        File(d, mocName).writeBytes(
            byteArrayOf('M'.code.toByte(), 'O'.code.toByte(), 'C'.code.toByte(), '3'.code.toByte(),
                revision.toByte()) + ByteArray(4),
        )
    }

    @Test
    fun `r7 模型过不了闸就不进形象轮盘（切过去就是下一帧 SIGSEGV）`() {
        val root = DhLabBootstrap.assetStore(context).cacheRoot(EngineAssets.LIVE2D)
        seed(root, "ATRI", "atri_8.moc3", 4)
        seed(root, "OriginalDemo", "OriginalDemo.moc3", 7)
        assertEquals(listOf("ATRI"), live2dPresentModels(context))
    }

    @Test
    fun `r5 的 OriginalDemo 照常进轮盘（Core 5r5 起可用）`() {
        val root = DhLabBootstrap.assetStore(context).cacheRoot(EngineAssets.LIVE2D)
        seed(root, "ATRI", "atri_8.moc3", 4)
        seed(root, "OriginalDemo", "OriginalDemo.moc3", 5)
        assertEquals(listOf("ATRI", "OriginalDemo"), live2dPresentModels(context))
    }
}
