package com.openapi.chat.presentation.ui

import com.openapi.core.share.ShareKit
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import com.openapi.chatkit.model.AttachmentActionKind
import com.openapi.chatkit.model.AttachmentPathInfo
import com.openapi.chatkit.model.AttachmentTarget
import java.io.File
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 附件长按菜单的动作：决定每种附件有哪些动作，并在 app 里执行（下载、复制图片、分享、复制路径）。
 * 「查看路径」由组件自己显示路径卡片，这里不处理。
 */
object AttachmentActions {

    /** 已另存到公共相册的图片：来源 -> 显示用的路径（如 Pictures/chat_xxx.jpg）。只在本次运行内记录。 */
    private val savedPaths = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * 「查看路径」卡片的内容：应用内路径（本地文件，或已下载到缓存的图片）、大小、
     * 以及已另存到相册的路径。远程图片还没下载时，应用内路径就是它的地址，没有大小。
     */
    fun infoFor(context: Context, target: AttachmentTarget): AttachmentPathInfo? = when (target) {
        is AttachmentTarget.File -> AttachmentPathInfo(
            name = target.file.name,
            path = target.file.path,
            sizeBytes = target.file.sizeBytes,
        )
        is AttachmentTarget.Image -> {
            val file = if (target.source.startsWith("http")) {
                File(context.cacheDir, "attachments/img_" + target.source.hashCode().toUInt().toString() + ".jpg")
                    .takeIf { it.exists() }
            } else {
                File(target.source)
            }
            AttachmentPathInfo(
                name = file?.name ?: target.source.substringAfterLast('/'),
                path = file?.path ?: target.source,
                sizeBytes = file?.length(),
                savedPath = savedPaths[target.source],
            )
        }
    }

    fun actionsFor(target: AttachmentTarget): List<AttachmentActionKind> = when (target) {
        is AttachmentTarget.Image -> listOf(
            AttachmentActionKind.Download,
            AttachmentActionKind.CopyImage,
            AttachmentActionKind.Share,
            AttachmentActionKind.ShowPath,
        )
        is AttachmentTarget.File -> listOf(
            AttachmentActionKind.Share,
            AttachmentActionKind.CopyPath,
            AttachmentActionKind.ShowPath,
        )
    }

    /** 执行一个动作。网络读取与文件写入都在 IO 线程，结果用 Toast 提示。 */
    suspend fun perform(context: Context, target: AttachmentTarget, kind: AttachmentActionKind) {
        when (kind) {
            AttachmentActionKind.ShowPath -> Unit
            AttachmentActionKind.CopyPath -> {
                val path = (target as? AttachmentTarget.File)?.file?.path ?: return
                ChatPlatform.copy(context, path)
            }
            AttachmentActionKind.Download -> {
                val image = target as? AttachmentTarget.Image ?: return
                val saved = withContext(Dispatchers.IO) { saveToPictures(context, image.source) }
                if (saved != null) savedPaths[image.source] = "Pictures/$saved"
                toast(context, if (saved != null) "已保存到相册" else "保存失败")
            }
            AttachmentActionKind.CopyImage -> {
                val image = target as? AttachmentTarget.Image ?: return
                val uri = withContext(Dispatchers.IO) { imageUri(context, image.source) }
                if (uri == null) {
                    toast(context, "复制失败")
                    return
                }
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "image", uri))
                toast(context, "已复制图片")
            }
            AttachmentActionKind.Share -> {
                // 统一走分享库：复制到分享目录、生成 content:// 地址、应用内分享面板。图片和文件共用一个入口
                val file = withContext(Dispatchers.IO) {
                    when (target) {
                        is AttachmentTarget.Image -> localImageFile(context, target.source)
                        is AttachmentTarget.File -> File(target.file.path).takeIf { it.isFile }
                    }
                }
                if (file == null) {
                    toast(context, if (target is AttachmentTarget.Image) "图片下载失败，无法分享" else "文件不存在，无法分享")
                    return
                }
                val name = (target as? AttachmentTarget.File)?.file?.name ?: file.name
                ShareKit.shareFile(context, file, displayName = name)
            }
        }
    }

    /** 图片：本地文件直接用；地址先下载到缓存目录。返回可以放进剪贴板的 content:// 地址（分享库生成）。 */
    private fun imageUri(context: Context, source: String): Uri? {
        val file = localImageFile(context, source) ?: return null
        return ShareKit.shareableUri(context, file)
    }

    private fun localImageFile(context: Context, source: String): File? {
        if (!source.startsWith("http")) return File(source).takeIf { it.exists() }
        val dir = File(context.cacheDir, "attachments").apply { mkdirs() }
        val file = File(dir, "img_" + source.hashCode().toUInt().toString() + ".jpg")
        if (!file.exists()) {
            runCatching { URL(source).openStream().use { input -> file.outputStream().use { input.copyTo(it) } } }
                .onFailure { return null }
        }
        return file.takeIf { it.exists() && BitmapFactory.decodeFile(it.path) != null }
    }

    /** 保存到公共相册 Pictures 目录（Android 10 及以上用 MediaStore，不需要存储权限）。成功返回文件名。 */
    private fun saveToPictures(context: Context, source: String): String? {
        val file = localImageFile(context, source) ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val name = "chat_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        val ok = runCatching {
            resolver.openOutputStream(target)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        }.isSuccess
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(target, values, null, null)
        if (!ok) {
            resolver.delete(target, null, null)
            return null
        }
        return name
    }

    private fun mimeOf(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }

    private fun toast(context: Context, text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
