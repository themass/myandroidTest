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
     * 走 [PhotoRenderer.renderStrict]（失败返回 null），**不走** [PhotoRenderer.render]：
     * 后者失败时退回原图引用，那是预览要的语义。导出照抄就成了数据损失——用户裁过、
     * 加过滤镜，落盘并覆盖暂存的却是原图，界面上还没有任何提示。所以这里失败就返回 null，
     * 并且**一个文件都不写**（宁可让宿主提示「编辑失败，已使用原图」，也不留一张假装成功的图）。
     *
     * 导出是一次性全分辨率路径，不复用预览的 [RenderBaseCache]（每会话一实例，缓存的是降档帧）。
     *
     * @param assets 滤镜 `.cube` 的来源；滤镜在降采样之前跑，所以导出用的是全分辨率像素
     * @return 写好的文件对应的 `file://` Uri；渲染或写盘失败返回 null
     */
    fun export(
        source: Bitmap,
        edits: PhotoEdits,
        cacheDir: File,
        assets: android.content.res.AssetManager,
    ): Uri? = try {
        // renderStrict 返回 null = 渲染失败。这里不能兜底成源图（那正是本函数要消灭的语义），
        // 也不用 ?.let 之外的写法绕回来：失败就一路 null 到调用方。
        PhotoRenderer.renderStrict(source, edits, lutProvider = LutAssets.provider(assets))?.let { rendered ->
            val scaled = scaleForUpload(rendered)
            val dir = File(cacheDir, DIR).apply { mkdirs() }
            val hasAlpha = scaled.hasAlpha()
            val file = File(dir, "edited_${System.currentTimeMillis()}.${if (hasAlpha) "png" else "jpg"}")
            val compressed = FileOutputStream(file).use { out ->
                val format = if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                scaled.compress(format, if (hasAlpha) 100 else JPEG_QUALITY, out)
            }
            // 只回收**本函数自己造的**位图。identity edits 时 renderStrict 原样返回调用方的
            // source，而 ChatRoute 的预览还在用它（pendingEdit = null 只是发信号，真正卸载要等
            // 一次重组，导出却先在 IO 线程跑完）——回收它，下一次绘制就是
            // 「Canvas: trying to use a recycled bitmap」。source 的命由调用方定，这里一律不碰。
            // （可达：decode 的采样只保证长边 < 4096，4000px 的照片就是整幅解出来 > MAX_LONG_EDGE，
            // 于是 scaleForUpload 必然另造一张 → scaled ≠ rendered。）
            if (scaled !== rendered && rendered !== source && !rendered.isRecycled) rendered.recycle()
            // 编码器失败时 compress 只是返回 false，不抛：留下的是一张半截文件 + 一个能打开的 Uri，
            // 下游（覆盖暂存 → 发送）看不出任何异常。和「静默回退原图」是同一种假成功，一并拒掉。
            if (compressed) Uri.fromFile(file) else {
                file.delete()
                null
            }
        }
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
