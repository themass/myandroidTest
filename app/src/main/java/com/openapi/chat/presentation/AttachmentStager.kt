package com.openapi.chat.presentation

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.aimath.paizhao.photosolve.ImageUtils
import com.aimath.paizhao.photosolve.image.FormatPolicy
import com.aimath.paizhao.photosolve.image.ImageUploadEncoder
import com.aimath.paizhao.photosolve.quality.QualityIssue
import com.aimath.paizhao.photosolve.quality.QuestionImagePreparer
import com.openapi.chat.domain.model.MessageKind
import java.io.File
import kotlin.math.roundToInt

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
 * 图片发送前的审阅结果：原图、可能存在的裁剪图，以及质量判断。
 * 两个文件都已经落盘；用户选定后再发送，未选中的那个由 [AttachmentStager.discardReview] 清理。
 */
data class ImageReview(
    val original: File,
    val cropped: File?,
    val issues: List<QualityIssue>,
    val detector: String?,
)

/**
 * 把系统选择器返回的 content:// 地址转成应用私有目录里的本地文件。
 *
 * 复制而不是保存原始 uri：content uri 的读权限随选择器会话失效，重启后历史消息里的图片
 * 会打不开。客户端负责限制大小，超限在选择时就拒绝，不等上传失败：
 * - 图片：长边缩到 [MAX_IMAGE_EDGE_PX] 以内，转 WebP（不上传原图），压缩后仍超过 [MAX_IMAGE_BYTES] 则拒绝。
 * - 文件：原始大小超过 [MAX_FILE_BYTES] 则拒绝，不复制。
 */
object AttachmentStager {

    private const val DIR = "chat_attachments"

    /** 图片长边上限（像素）。 */
    const val MAX_IMAGE_EDGE_PX = 2048

    /** 图片压缩后体积上限。 */
    const val MAX_IMAGE_BYTES = 5L * 1024 * 1024

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
        val scaled = scaleToLongEdge(decoded, MAX_IMAGE_EDGE_PX)
        val encoded = ImageUploadEncoder.encode(scaled, FormatPolicy.WEBP)
        if (encoded.bytes.size > MAX_IMAGE_BYTES) {
            return StageOutcome.Rejected("图片压缩后仍超过 5 MB，请换一张小一些的图片")
        }
        val file = File(dir, "img_$stamp.${encoded.format.name.lowercase()}")
        file.writeBytes(encoded.bytes)
        return StageOutcome.Staged(StagedAttachment(file = file, displayName = file.name, sizeBytes = file.length()))
    }

    /** 缩到长边不超过 [maxEdge]；已经够小就原样返回。 */
    private fun scaleToLongEdge(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longEdge
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
            (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    /**
     * 图片先做题目识别与质量判断，再分别落盘原图与裁剪图（都经过同一套压缩策略）。
     * 解码失败返回 null。
     */
    suspend fun stageImageForReview(context: Context, uri: Uri, preparer: QuestionImagePreparer): ImageReview? {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val bitmap = ImageUtils.decodeUriApplyingExifOrientation(context, uri) ?: return null
        val prepared = preparer.prepare(bitmap)

        val originalEncoded = ImageUploadEncoder.encode(bitmap)
        val original = File(dir, "review_${stamp}_original.${originalEncoded.format.name.lowercase()}")
        original.writeBytes(originalEncoded.bytes)
        val cropped = prepared.cropped?.let { c ->
            val encoded = ImageUploadEncoder.encode(c)
            File(dir, "review_${stamp}_cropped.${encoded.format.name.lowercase()}").also { it.writeBytes(encoded.bytes) }
        }
        return ImageReview(
            original = original,
            cropped = cropped,
            issues = prepared.quality.issues,
            detector = prepared.detector,
        )
    }

    /** 清理审阅时产生的文件；[keep] 为用户选中要发送的那一个，传 null 则全部删除。 */
    fun discardReview(review: ImageReview, keep: File?) {
        listOfNotNull(review.original, review.cropped)
            .filter { it != keep }
            .forEach { runCatching { it.delete() } }
    }

    private fun stageFile(context: Context, uri: Uri, dir: File, stamp: Long): StageOutcome {
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
