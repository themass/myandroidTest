package com.openapi.chat.presentation

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.aimath.paizhao.photosolve.ImageUtils
import com.aimath.paizhao.photosolve.image.ImageUploadEncoder
import com.openapi.chat.domain.model.MessageKind
import java.io.File

/** 已经落到应用私有目录、可以直接上传的附件。 */
data class StagedAttachment(
    val file: File,
    val displayName: String,
    val sizeBytes: Long,
)

/**
 * 把系统选择器返回的 content:// 地址转成应用私有目录里的本地文件。
 *
 * 复制而不是保存原始 uri：content uri 的读权限随选择器会话失效，重启后历史消息里的图片
 * 会打不开。图片同时在这里压缩（与拍照解题同一套编码策略），上传的就是这个文件。
 */
object AttachmentStager {

    private const val DIR = "chat_attachments"

    fun stage(context: Context, kind: MessageKind, uri: Uri): StagedAttachment? {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        return when (kind) {
            MessageKind.IMAGE -> stageImage(context, uri, dir, stamp)
            MessageKind.FILE -> stageFile(context, uri, dir, stamp)
            else -> null
        }
    }

    private fun stageImage(context: Context, uri: Uri, dir: File, stamp: Long): StagedAttachment? {
        val bitmap = ImageUtils.decodeUriApplyingExifOrientation(context, uri) ?: return null
        val encoded = ImageUploadEncoder.encode(bitmap)
        val file = File(dir, "img_$stamp.${encoded.format.name.lowercase()}")
        file.writeBytes(encoded.bytes)
        return StagedAttachment(file = file, displayName = file.name, sizeBytes = file.length())
    }

    private fun stageFile(context: Context, uri: Uri, dir: File, stamp: Long): StagedAttachment? {
        val name = displayName(context, uri)?.replace(Regex("[\\\\/:*?\"<>|]"), "_")?.takeIf { it.isNotBlank() }
            ?: "file_$stamp"
        val file = File(dir, "file_${stamp}_$name")
        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { input.copyTo(it) }
            true
        } ?: false
        if (!copied) return null
        return StagedAttachment(file = file, displayName = name, sizeBytes = file.length())
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}
