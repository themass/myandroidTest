package com.openapi.mediaview.edit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.max

/**
 * [PhotoEditExporter.export]：导出路径**不许**静默回退原图。
 *
 * 为什么这条约束值得单独钉住：`PhotoRenderer.render` 的兜底（失败就返回原图引用）是为预览设计的
 * ——预览必须有图可显示。导出照抄这个语义就是数据损失：用户裁过、加过滤镜，落盘的却是原图，
 * 而且界面上一句提示都没有。所以导出走 [PhotoRenderer.renderStrict]（失败返回 null），
 * 失败时一个文件都不该留下，由宿主（ChatRoute）明确告诉用户「用了原图」。
 *
 * 整类走 Robolectric + NATIVE 图形：真的压缩、真的落盘、真的解码回来比对。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PhotoEditExporterTest {

    @get:Rule
    val folder = TemporaryFolder()

    /**
     * 有细节的源图：渐变 + 高频项。
     *
     * 高频项是给「文件 >1KB」这条断言兜底的——纯渐变 JPEG 只压出 500 字节，
     * 那条断言就变成了「fixture 恰好好看」而不是「写出了正常体积的图」。
     * 频率固定（不取随机数），所以文件体积、像素值都是确定的。
     */
    private fun source(w: Int = 200, h: Int = 150): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (x in 0 until w) for (y in 0 until h) {
            val v = x * 255 / (w - 1)
            val n = (x * 73 + y * 151) % 256
            b.setPixel(x, y, Color.rgb((v + n) % 256, v, (255 - v + n) % 256))
        }
        return b
    }

    /** 导出目录（[PhotoEditExporter] 内部的 `photo_edit`）里已有的文件名。 */
    private fun exportedFiles(cacheDir: java.io.File): List<String> =
        java.io.File(cacheDir, "photo_edit").list()?.toList() ?: emptyList()

    /**
     * 往 [LutAssets] 的进程级记忆里塞一张表。
     *
     * 只测**注入**这一件事用反射：导出路径里 provider 是 `export` 自己从 AssetManager 构造的
     * （计划定案，签名不接受注入），测试想稳定地让「滤镜那一步炸掉」只能从表本身下手。
     * id 用唯一串，不和其它测试抢这张全局记忆。
     */
    private fun injectLut(id: String, lut: Lut3D?) {
        val field = LutAssets::class.java.getDeclaredField("memo")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(LutAssets) as MutableMap<String, Lut3D?>)[id] = lut
    }

    private fun assets() = RuntimeEnvironment.getApplication().assets

    @Test
    fun identityEditsWriteARealFile() {
        // 回归：导出主路径（没编辑过 = 原样落盘）本来就是没测试覆盖的
        val cacheDir = folder.newFolder("cache")
        val src = source()

        val uri = PhotoEditExporter.export(src, PhotoEdits(src.width, src.height), cacheDir, assets())

        assertNotNull(uri)
        val file = java.io.File(uri!!.path!!)
        assertTrue("导出文件必须存在：$file", file.exists())
        assertTrue("导出文件不该是半截空文件（${file.length()} 字节）", file.length() > 1024)
        val decoded = BitmapFactory.decodeFile(file.path)
        assertNotNull("落盘的必须是一张能解码的图", decoded)
        assertEquals(src.width, decoded!!.width)
        assertEquals(src.height, decoded.height)
        assertEquals("导出目录里就该只有这一份结果", 1, exportedFiles(cacheDir).size)
    }

    /**
     * 滤镜那一步炸掉（这里是表形状不合预期 → 采样越界）时，导出必须返回 null，
     * 而且**不留下任何文件**——留下半截文件等于让用户以为编辑成功了。
     *
     * 顺带钉住 provider 接线：`export` 只在有滤镜时才问表，命中记忆化说明它真的把
     * AssetManager 版 provider 传进了渲染管线（漏传 lutProvider 的话这里会拿到「无滤镜」，
     * 于是渲染成功、文件写出、断言失败）。
     */
    @Test
    fun corruptFilterTableFailsTheExportWithoutWritingAnything() {
        val cacheDir = folder.newFolder("cache")
        val poison = "task7-export-corrupt-table"
        // 声明 33³ 却只有 3 个分量：LutApply 采样必然 IndexOutOfBounds（RuntimeException）
        injectLut(poison, Lut3D(33, FloatArray(3)))
        val src = source()

        val uri = PhotoEditExporter.export(
            src,
            PhotoEdits(src.width, src.height).push(EditOp.Filter(poison, 100)),
            cacheDir,
            assets(),
        )

        assertNull("滤镜渲染失败必须让导出失败", uri)
        assertEquals("失败时一个文件都不该写：${exportedFiles(cacheDir)}", emptyList<String>(), exportedFiles(cacheDir))

        // 反证：同一个 cacheDir、同一张源图，不套坏表就能正常写出来
        //（否则上面那条「没有文件」断言只是「导出根本什么都没做」）
        val ok = PhotoEditExporter.export(src, PhotoEdits(src.width, src.height), cacheDir, assets())
        assertNotNull(ok)
        assertEquals(1, exportedFiles(cacheDir).size)
    }

    /** 带标注的导出：标注要真的进落盘的那张图（覆盖 onDone → 覆盖暂存文件那条链的产出物）。 */
    @Test
    fun annotationIsBakedIntoTheExportedFile() {
        val cacheDir = folder.newFolder("cache")
        val src = source(120, 80)
        val edits = PhotoEdits(src.width, src.height).push(
            EditOp.Stroke(
                points = listOf(Offset(0.05f, 0.5f), Offset(0.95f, 0.5f)),
                colorArgb = 0xFFFF0000L,
                widthRatio = 0.2f,
            ),
        )

        val uri = PhotoEditExporter.export(src, edits, cacheDir, assets())

        assertNotNull(uri)
        val decoded = BitmapFactory.decodeFile(java.io.File(uri!!.path!!).path)
        assertNotNull(decoded)
        // 中线一段：源图在中线附近的像素不是红的
        val mid = decoded!!.getPixel(decoded.width / 2, decoded.height / 2)
        assertTrue("导出图的中线应被红笔迹盖住，实际 ${Integer.toHexString(mid)}", Color.red(mid) > 200 && Color.green(mid) < 90 && Color.blue(mid) < 90)
    }

    /**
     * 导出**不许**回收调用方传进来的位图。
     *
     * identity edits 下 renderStrict 原样返回 [source]；源图长边又超过 [PhotoEditExporter.MAX_LONG_EDGE]
     * 时 `scaleForUpload` 会另造一张缩小的，于是「scaled ≠ rendered 就回收 rendered」这条清理
     * 逻辑回收的其实是**调用方的**那张——而 ChatRoute 里的 PhotoEditor 还在用它做预览
     * （`pendingEdit = null` 只是发出信号，真正卸载要等一次重组，而导出在 IO 线程上先跑完了）。
     * 之后谁再拿它画一次就是 `Canvas: trying to use a recycled bitmap`。
     */
    @Test
    fun exportNeverRecyclesTheCallersBitmap() {
        val cacheDir = folder.newFolder("cache")
        // 长边 > MAX_LONG_EDGE：scaleForUpload 一定会另造一张。尺寸照 brief 真机验收那档（4000×3000）
        // 往下取一半，只为省时间；逐像素填色太慢，这条测试只关心「谁被回收了」。
        val src = Bitmap.createBitmap(2600, 1600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(10, 120, 200)) }

        val uri = PhotoEditExporter.export(src, PhotoEdits(src.width, src.height), cacheDir, assets())

        assertNotNull(uri)
        assertFalse("导出把调用方的位图回收了", src.isRecycled)
        // 反证：源图确实大到需要缩放，否则这条测试什么都没钉住
        assertTrue(src.width > PhotoEditExporter.MAX_LONG_EDGE)
        val decoded = BitmapFactory.decodeFile(java.io.File(uri!!.path!!).path)
        assertEquals(PhotoEditExporter.MAX_LONG_EDGE, max(decoded!!.width, decoded.height))
    }
}
