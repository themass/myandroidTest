package com.openapi.chat.presentation

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.aimath.paizhao.photosolve.ImageUtils
import com.openapi.core.image.ImageCompressor
import com.openapi.chat.domain.model.MessageKind
import java.io.File

/** 已经落到应用私有目录、可以直接上传的附件。 */
data class StagedAttachment(
    val file: File,
    val displayName: String,
    val sizeBytes: Long,
)

/** 附件落盘的结果：成功、因超限被拒（带给用户看的原因）、读取失败。 */
sealed interface StageOutcome {
    data class Staged(val attachment: StagedAttachment) : StageOutcome
    data class Rejected(val reason: String) : StageOutcome
    data object Failed : StageOutcome
}

/**
 * 把系统选择器返回的 content:// 地址转成应用私有目录里的本地文件。
 *
 * 复制而不是保存原始 uri：content uri 的读权限随选择器会话失效，重启后历史消息里的图片
 * 会打不开。客户端负责限制大小，超限在选择时就拒绝，不等上传失败：
 * - 图片：统一用 [ImageCompressor] 压缩后保存为 JPEG；
 * - 文件：原始大小超过 [MAX_FILE_BYTES] 则拒绝，不复制。
 */
object AttachmentStager {

    private const val DIR = "chat_attachments"

    /** 文件体积上限（原始大小）。 */
    const val MAX_FILE_BYTES = 20L * 1024 * 1024

    fun stage(context: Context, kind: MessageKind, uri: Uri): StageOutcome {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        return when (kind) {
            MessageKind.IMAGE -> stageImage(context, uri, dir, stamp)
            MessageKind.FILE -> stageFile(context, uri, dir, stamp)
            else -> StageOutcome.Failed
        }
    }

    private fun stageImage(context: Context, uri: Uri, dir: File, stamp: Long): StageOutcome {
        val decoded = ImageUtils.decodeUriApplyingExifOrientation(context, uri) ?: return StageOutcome.Failed
        // 统一压缩：长边 1600、JPEG 质量 82、800 KB 以内（见 ImageCompressor）
        val result = ImageCompressor.compress(decoded)
        val file = File(dir, "img_$stamp.jpg")
        file.writeBytes(result.bytes)
        return StageOutcome.Staged(StagedAttachment(file = file, displayName = file.name, sizeBytes = file.length()))
    }

    private fun stageFile(context: Context, uri: Uri, dir: File, stamp: Long): StageOutcome {
        // 「文件」里选中的图片也要压缩，不能原样上传（否则和相册发出的图片大小差很多）
        if (context.contentResolver.getType(uri)?.startsWith("image/") == true) {
            val decoded = ImageUtils.decodeUriApplyingExifOrientation(context, uri)
            if (decoded != null) {
                val result = ImageCompressor.compress(decoded)
                val file = File(dir, "file_${stamp}_image.jpg")
                file.writeBytes(result.bytes)
                return StageOutcome.Staged(StagedAttachment(file = file, displayName = file.name, sizeBytes = file.length()))
            }
        }
        val (name, size) = displayNameAndSize(context, uri)
        if (size != null && size > MAX_FILE_BYTES) {
            return StageOutcome.Rejected("文件超过 20 MB，无法发送")
        }
        val safeName = name?.replace(Regex("[\\\\/:*?\"<>|]"), "_")?.takeIf { it.isNotBlank() } ?: "file_$stamp"
        val file = File(dir, "file_${stamp}_$safeName")
        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { input.copyTo(it) }
            true
        } ?: false
        if (!copied) return StageOutcome.Failed
        // 大小未知（部分选择器不给 SIZE）时，复制后再按实际大小判断
        if (file.length() > MAX_FILE_BYTES) {
            file.delete()
            return StageOutcome.Rejected("文件超过 20 MB，无法发送")
        }
        return StageOutcome.Staged(StagedAttachment(file = file, displayName = safeName, sizeBytes = file.length()))
    }

    private fun displayNameAndSize(context: Context, uri: Uri): Pair<String?, Long?> {
        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        cursor?.use { c ->
            if (c.moveToFirst()) {
                val name = c.getString(0)
                val size = if (c.isNull(1)) null else c.getLong(1)
                return name to size
            }
        }
        return null to null
    }
}
