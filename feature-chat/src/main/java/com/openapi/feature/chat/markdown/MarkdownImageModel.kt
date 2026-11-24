package com.openapi.feature.chat.markdown

import java.io.File

/** Resolves markdown image sources to Glide models, preferring attachment files already on disk. */
internal object MarkdownImageModel {
    @JvmStatic
    @JvmOverloads
    fun resolve(cacheDir: File, source: String, fileExists: (File) -> Boolean = { it.exists() }): Any {
        if (source.startsWith("http")) {
            val cached = cachedAttachmentFile(cacheDir, source)
            return if (fileExists(cached)) cached else source
        }
        if (source.startsWith("content:") || source.startsWith("file:")) return source
        return File(source)
    }

    fun cachedAttachmentFile(cacheDir: File, source: String): File {
        val name = "img_${source.hashCode().toUInt()}.jpg"
        return File(File(cacheDir, "attachments"), name)
    }
}
