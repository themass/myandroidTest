package com.openapi.feature.chat.presentation

import android.media.MediaMetadataRetriever
import java.io.File

/**
 * 从本地视频文件读「时长 + 首帧缩略图」。单独抽成接口只有一个原因：
 * `MediaMetadataRetriever` 是 Android framework 类，JVM 单测里 new 不出来——
 * 暂存流程（预检、落盘、写草稿）要在测试里跑通，就得能把这里换成假的。
 */
internal interface VideoMetaSource {
    /** 时长（秒），读不出来返回 0（气泡不显示时长，而不是显示 0:00 骗人）。 */
    fun durationSeconds(file: File): Int

    /** 把首帧写成 JPEG 缩略图，返回路径；失败返回 null（气泡退化成文件名卡片）。 */
    fun firstFrameThumbnail(file: File, target: File): String?
}

/** 真机实现：`MediaMetadataRetriever`（与 :retromusic 的用法同款）。 */
internal object AndroidVideoMeta : VideoMetaSource {

    override fun durationSeconds(file: File): Int = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val millis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            // 四舍五入到秒：1499ms 显示 0:01 比 0:00 更接近用户看到的
            ((millis + 500) / 1000).toInt()
        } finally {
            retriever.release()
        }
    }.getOrDefault(0)

    override fun firstFrameThumbnail(file: File, target: File): String? = runCatching {
        val retriever = MediaMetadataRetriever()
        val bitmap = try {
            retriever.setDataSource(file.absolutePath)
            // 首帧可能不是关键帧（取回 null），退到「第一个可用帧」；尺寸交给解码器原样，
            // 缩略图只进气泡，不做缩放——服务端不产缩略图，端上再压一遍不值得加依赖
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime()
        } finally {
            retriever.release()
        }
        if (bitmap == null) return@runCatching null
        val ok = target.outputStream().use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
        }
        bitmap.recycle()
        if (ok) target.absolutePath else null
    }.getOrNull()
}
