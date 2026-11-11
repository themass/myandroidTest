package com.openapi.mediaview.edit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 把编辑后的图片落到缓存目录，返回一个 `file://` 形式的 [Uri]。
 *
 * 为什么落盘而不是直接把 Bitmap 传回对话：
 * - 对话的图片消息本来就以「应用私有目录里的文件路径」为准（[com.openapi.feature.chat] 的
 *   `ImageAttachment.path`），传 Bitmap 会让 ViewModel 和界面耦合到像素格式、生命周期和回收时机；
 * - 落盘后可以复用现有的压缩、上传、EXIF 处理链路，大图也不会在内存里多留一份。
 *
 * 约定：
 * - 目录 `cacheDir/photo_edit/`，文件名带时间戳，**不会覆盖原图**；
 * - 存成 JPEG（质量 92；带透明通道时存 PNG），长边最多 [MAX_LONG_EDGE]；
 * - 尺寸上限在裁剪/渲染阶段已经处理过一次（[PhotoRenderer.MAX_OUTPUT_LONG_EDGE]），这里再按
 *   上传用的尺寸兜一次底；
 * - 调用方负责在合适的时候清理（缓存目录本来就会被系统回收；也可以主动调用 [clearCache]）。
 */
object PhotoEditExporter {

    /** 编辑结果的长边上限。和图片编辑的典型使用场景一致：够看清标注，又不至于几 MB。 */
    const val MAX_LONG_EDGE = 2048

    const val JPEG_QUALITY = 92

    private const val DIR = "photo_edit"

    /**
     * 解码用户选中的图片（正确处理 EXIF 方向），并按长边上限采样，避免原图 8000px 直接进内存。
     * 返回 null 表示不是可解码的图片。
     */
    fun decode(uri: Uri, resolver: android.content.ContentResolver, maxLongEdge: Int = MAX_LONG_EDGE): Bitmap? {
        return try {
            resolver.openInputStream(uri)?.use { input ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
                var sample = 1
                val longEdge = max(bounds.outWidth, bounds.outHeight)
                while (longEdge / (sample * 2) >= maxLongEdge) sample *= 2
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 渲染 [edits] 并写入缓存目录。
     *
     * @return 写好的文件对应的 `file://` Uri；失败返回 null
     */
    fun export(
        source: Bitmap,
        edits: PhotoEdits,
        cacheDir: File,
    ): Uri? = try {
        val rendered = PhotoRenderer.render(source, edits)
        val scaled = scaleForUpload(rendered)
        val dir = File(cacheDir, DIR).apply { mkdirs() }
        val hasAlpha = scaled.hasAlpha()
        val file = File(dir, "edited_${System.currentTimeMillis()}.${if (hasAlpha) "png" else "jpg"}")
        FileOutputStream(file).use { out ->
            val format = if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            scaled.compress(format, if (hasAlpha) 100 else JPEG_QUALITY, out)
        }
        if (scaled !== rendered && !rendered.isRecycled) rendered.recycle()
        Uri.fromFile(file)
    } catch (e: Exception) {
        null
    }

    private fun scaleForUpload(source: Bitmap): Bitmap {
        val longEdge = max(source.width, source.height)
        if (longEdge <= MAX_LONG_EDGE) return source
        val ratio = MAX_LONG_EDGE.toFloat() / longEdge
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).roundToInt().coerceAtLeast(1),
            (source.height * ratio).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    /** 删除缓存目录里的编辑结果（例如用户取消发送）。 */
    fun clearCache(cacheDir: File) {
        val dir = File(cacheDir, DIR)
        dir.listFiles()?.forEach { it.delete() }
    }

    /** 删除单个编辑结果，避免取消后长期占着缓存。 */
    fun delete(uri: Uri) {
        if (uri.scheme == "file") {
            runCatching { File(uri.path ?: return).delete() }
        }
    }

    /** 供界面显示「编辑后尺寸」用。 */
    fun estimatedSize(edits: PhotoEdits): Pair<Int, Int> {
        val longEdge = max(edits.displayWidth, edits.displayHeight)
        if (longEdge <= MAX_LONG_EDGE) return edits.displayWidth to edits.displayHeight
        val ratio = MAX_LONG_EDGE.toFloat() / longEdge
        return (edits.displayWidth * ratio).roundToInt() to (edits.displayHeight * ratio).roundToInt()
    }
}
