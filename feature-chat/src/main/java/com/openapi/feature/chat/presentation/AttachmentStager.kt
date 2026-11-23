package com.openapi.feature.chat.presentation

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.openapi.common.image.ImageUtils
import com.openapi.feature.chat.R
import com.openapi.common.image.ImageCompressor
import com.openapi.feature.chat.domain.model.MessageKind
import com.openapi.feature.chat.domain.model.VideoRules
import java.io.File

/** 已经落到应用私有目录、可以直接上传的附件。 */
data class StagedAttachment(
    val file: File,
    val displayName: String,
    val sizeBytes: Long,
    /** 视频时长（秒），暂存时从本地文件读出；图片/文件恒为 0。 */
    val durationSeconds: Int = 0,
    /** 视频首帧缩略图的本地路径（发送侧气泡显示用）；读不出来为 null。 */
    val thumbnailPath: String? = null,
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

    /**
     * 暂存目录，顺带扫掉超龄的孤儿文件（cleanup 项 5）。
     *
     * 孤儿的来源是这条路的形状：选多张时超出附件额度那张被闸挡下、选完就退出、编辑半途取消、
     * 拍照取消——那些字节从没进过任何托盘，以前**没有任何回收路径**（Task 4 报告 §9 登记的就是
     * 这个缺口）。清扫逻辑在 :common 的 [com.openapi.common.media.StagingGc]，两处页面共用一份
     * （feature 之间不许互相依赖，第 0 层是唯一能共用的地方）。
     *
     * 挂在「用到目录的时候」而不是 Application 启动：年龄阈值 7 天意味着任何一次经过这里
     * 都够把陈旧孤儿清掉，不必给 app 加一条启动时序，也不会误删另一次会话正在用的文件。
     */
    fun stagingDir(context: Context): File {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        com.openapi.common.media.StagingGc.sweep(dir)
        return dir
    }

    fun stage(context: Context, kind: MessageKind, uri: Uri): StageOutcome =
        stage(context, kind, uri, AndroidVideoMeta)

    /** 可注入 [meta] 的重载：视频时长/首帧在 JVM 测试里读不了真编码器，换假的进来跑全流程。 */
    internal fun stage(context: Context, kind: MessageKind, uri: Uri, meta: VideoMetaSource): StageOutcome {
        val dir = stagingDir(context)
        val stamp = System.currentTimeMillis()
        return when (kind) {
            MessageKind.IMAGE -> stageImage(context, uri, dir, stamp)
            MessageKind.FILE -> stageFile(context, uri, dir, stamp)
            MessageKind.VIDEO -> stageVideo(context, uri, dir, stamp, meta)
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

    /**
     * 视频暂存：复制到私有目录，原样保存（不做任何转码——服务端不转码，端上转一遍只会更慢更糊）。
     *
     * 规则与 [com.openapi.feature.chat.domain.model.VideoRules] 完全一致，且在**复制之前**就拦：
     * 25 MiB 的文件复制一半再丢掉，浪费的是用户存储和 IO。选择器给出的显示名可能没有扩展名
     * （部分文件管理器），这时按 content type 兜底判 mp4。
     */
    private fun stageVideo(context: Context, uri: Uri, dir: File, stamp: Long, meta: VideoMetaSource): StageOutcome {
        val (name, size) = displayNameAndSize(context, uri)
        val displayName = name?.takeIf { it.isNotBlank() } ?: "video_$stamp.mp4"
        val declaredMp4 = VideoRules.isMp4(displayName) ||
            context.contentResolver.getType(uri) == "video/mp4"
        if (!declaredMp4) {
            return StageOutcome.Rejected(context.getString(R.string.chat_video_not_mp4))
        }
        if (size != null && size > VideoRules.MAX_VIDEO_BYTES) {
            return StageOutcome.Rejected(context.getString(R.string.chat_video_too_large))
        }
        val safeName = displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val file = File(dir, "vid_${stamp}_$safeName")
        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { input.copyTo(it) }
            true
        } ?: false
        if (!copied) {
            file.delete()
            return StageOutcome.Failed
        }
        // 大小未知时复制后按实际大小判断（与 stageFile 同款兜底）
        if (file.length() > VideoRules.MAX_VIDEO_BYTES) {
            file.delete()
            return StageOutcome.Rejected(context.getString(R.string.chat_video_too_large))
        }
        // 时长与首帧读失败都不拦发送：时长只是显示用，读不出来就不显示；
        // 缩略图读不出来气泡退化成文件名卡片。为显示功能拒发一条真视频是本末倒置。
        val duration = meta.durationSeconds(file)
        val thumb = meta.firstFrameThumbnail(file, File(dir, "vid_${stamp}_thumb.jpg"))
        return StageOutcome.Staged(
            StagedAttachment(
                file = file,
                displayName = safeName,
                sizeBytes = file.length(),
                durationSeconds = duration,
                thumbnailPath = thumb,
            ),
        )
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
            return StageOutcome.Rejected(context.getString(R.string.chat_file_too_large))
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
            return StageOutcome.Rejected(context.getString(R.string.chat_file_too_large))
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
