package com.openapi.chat.presentation.ui

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
import androidx.core.content.FileProvider
import com.openapi.chatkit.model.AttachmentActionKind
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
                val ok = withContext(Dispatchers.IO) { saveToPictures(context, image.source) }
                toast(context, if (ok) "已保存到相册" else "保存失败")
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
                val (uri, mime) = withContext(Dispatchers.IO) {
                    when (target) {
                        is AttachmentTarget.Image -> imageUri(context, target.source) to "image/*"
                        is AttachmentTarget.File -> fileUri(context, target.file.path) to mimeOf(target.file.name)
                    }
                }
                if (uri == null) {
                    toast(context, "分享失败")
                    return
                }
                val send = Intent(Intent.ACTION_SEND)
                    .setType(mime)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, null))
            }
        }
    }

    /** 图片：本地文件直接用；地址先下载到缓存目录。返回可以分享/复制的 FileProvider uri。 */
    private fun imageUri(context: Context, source: String): Uri? {
        val file = localImageFile(context, source) ?: return null
        return FileProvider.getUriForFile(context, FILE_PROVIDER, file)
    }

    private fun fileUri(context: Context, path: String): Uri? = runCatching {
        FileProvider.getUriForFile(context, FILE_PROVIDER, File(path))
    }.getOrNull()

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

    /** 保存到公共相册 Pictures 目录（Android 10 及以上用 MediaStore，不需要存储权限）。 */
    private fun saveToPictures(context: Context, source: String): Boolean {
        val file = localImageFile(context, source) ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "chat_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        val ok = runCatching {
            resolver.openOutputStream(target)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        }.isSuccess
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(target, values, null, null)
        if (!ok) resolver.delete(target, null, null)
        return ok
    }

    private fun mimeOf(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }

    private fun toast(context: Context, text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    private const val FILE_PROVIDER = "com.openapi.ks.chat.fileprovider"
}
