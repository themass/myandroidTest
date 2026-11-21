package com.openapi.feature.video.prototype

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import java.io.File

/**
 * 打开本地视频时自动找同名字幕（设计稿：「打开本地视频后，若用户已授权目录，可以匹配同名字幕
 * （如 `Film.zh.srt`），否则用系统文件选择器；不申请全盘权限」）。
 *
 * 只在视频所在目录里按文件名前缀找 SRT / WebVTT。目录读不到（Android 11 的分区存储）就返回空，
 * 由用户手动从文档选择器挑，绝不申请全盘权限。
 */
internal object SidecarSubtitles {

    private val EXTENSIONS = listOf("srt", "vtt")

    /** 返回与视频同目录、同名的字幕文件（`Film.mp4` → `Film.zh.srt` / `Film.srt`）。 */
    fun find(resolver: ContentResolver, videoUrl: String?): File? {
        val video = resolveVideoFile(resolver, videoUrl) ?: return null
        val dir = video.parentFile?.takeIf { it.canRead() } ?: return null
        val baseName = video.nameWithoutExtension
        if (baseName.isEmpty()) return null

        val candidates = dir.listFiles { file ->
            val name = file.name
            val dot = name.lastIndexOf('.')
            if (dot <= 0) return@listFiles false
            val ext = name.substring(dot + 1).lowercase()
            ext in EXTENSIONS && name.substring(0, dot).startsWith(baseName, ignoreCase = true)
        }.orEmpty()

        // 同名的优先，其次 `Film.zh.srt` 这类语言后缀，挑最短的那个当主字幕
        return candidates.minByOrNull { it.name.length }
    }

    private fun resolveVideoFile(resolver: ContentResolver, videoUrl: String?): File? {
        if (videoUrl.isNullOrBlank()) return null
        return try {
            when {
                videoUrl.startsWith("/") -> File(videoUrl).takeIf { it.isFile }
                videoUrl.startsWith("file://") ->
                    Uri.parse(videoUrl).path?.let { File(it) }?.takeIf { it.isFile }
                videoUrl.startsWith("content://") -> {
                    resolver.query(
                        Uri.parse(videoUrl),
                        arrayOf(MediaStore.MediaColumns.DATA),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getString(0)?.let { File(it) }?.takeIf { it.isFile }
                        } else {
                            null
                        }
                    }
                }
                else -> null
            }
        } catch (ignored: Exception) {
            null
        }
    }
}
