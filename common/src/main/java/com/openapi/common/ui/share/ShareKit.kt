package com.openapi.common.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * 统一的分享入口：文字、文件、位图都走应用内分享面板（[ShareSheet]），拿不到 Activity 时退回系统选择器。
 *
 * 文件怎么交给别的应用：
 * 1. 先复制到 `cache/share/`（[prepare]）。只开放这一个目录，调用方给什么路径都能分享，
 *    也不会把聊天目录整个暴露出去；
 * 2. 用本库自己的 [ShareKitFileProvider] 生成 content:// 地址；
 * 3. 地址同时放进 EXTRA_STREAM 和 ClipData，并加读取权限。启动具体应用前再对它的包名单独授权一次，
 *    有的应用（如微信）在另一个 Activity 里读文件，只靠 Intent 上的权限不够。
 *
 * 图片分享不再附带文字：微信等收到「图片 + 文字」时会只当成文字处理，或者直接报错。
 */
object ShareKit {
    private const val TAG = "ShareKit"
    private const val SHARE_DIR = "share"

    /** 分享文字（链接、说明等）。 */
    fun shareText(
        context: Context,
        text: String,
        subject: String? = null,
        chooserTitle: String? = null,
        extraTitle: String? = null,
    ) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
            // 旧的 ShareUtil 会附带 EXTRA_TITLE，保留以兼容读取它的接收方
            if (extraTitle != null) putExtra(Intent.EXTRA_TITLE, extraTitle)
        }
        start(context, intent, null, chooserTitle)
    }

    /**
     * 分享一个文件。[mimeType] 不传时按扩展名猜测。[displayName] 是接收方看到的文件名，默认用原文件名。
     * 文件不存在或复制失败时提示并返回 false。
     */
    fun shareFile(
        context: Context,
        file: File,
        mimeType: String? = null,
        displayName: String? = null,
        chooserTitle: String? = null,
    ): Boolean {
        val prepared = prepare(context, file, displayName ?: file.name) ?: return false
        val uri = uriFor(context, prepared) ?: return false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType ?: guessMime(prepared.name)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(prepared.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return start(context, intent, uri, chooserTitle)
    }

    /** 分享一张位图：写成 PNG 后按文件分享。 */
    fun shareBitmap(
        context: Context,
        bitmap: Bitmap,
        name: String = "share_${System.currentTimeMillis()}.png",
        chooserTitle: String? = null,
    ): Boolean {
        val file = File(shareDir(context), name)
        return try {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            shareFile(context, file, "image/png", chooserTitle = chooserTitle)
        } catch (e: Exception) {
            Log.e(TAG, "write share bitmap failed", e)
            toast(context, "分享图片生成失败")
            false
        }
    }

    /**
     * 分享卡片（深色底 + 标题 + 二维码 + 链接）：在后台线程渲染成图片，再按图片分享。
     * 渲染失败时退回分享链接文字。
     */
    fun shareCard(context: Context, title: String, subtitle: String, link: String, chooserTitle: String? = null) {
        val app = context.applicationContext
        Thread {
            val bitmap = try {
                ShareCard.render(app, title, subtitle, link)
            } catch (e: Exception) {
                Log.e(TAG, "render share card failed", e)
                null
            }
            Handler(Looper.getMainLooper()).post {
                if (bitmap == null) {
                    toast(app, "分享图片生成失败，改为分享链接")
                    shareText(context, link, subject = title, chooserTitle = chooserTitle)
                } else {
                    shareBitmap(context, bitmap, chooserTitle = chooserTitle)
                }
            }
        }.start()
    }

    /** 文件对应的 content:// 地址；文件必须已经在 `cache/share/` 里（见 [prepare]）。 */
    fun uriFor(context: Context, file: File): Uri? = try {
        FileProvider.getUriForFile(context, authority(context), file)
    } catch (e: IllegalArgumentException) {
        Log.e(TAG, "file not shareable: ${file.path}", e)
        toast(context, "无法分享这个文件")
        null
    }

    /**
     * 任意本地文件 → 可以交给别的应用的 content:// 地址（复制到 `cache/share/` 后生成）。
     * 复制图片到剪贴板等场景也用它。
     */
    fun shareableUri(context: Context, file: File, displayName: String = file.name): Uri? {
        val prepared = prepare(context, file, displayName) ?: return null
        return uriFor(context, prepared)
    }

    fun authority(context: Context): String = context.packageName + ".sharekit"

    /** 把文件复制到 `cache/share/`（已经在里面就直接用）。不存在或复制失败返回 null。 */
    private fun prepare(context: Context, source: File, displayName: String): File? {
        if (!source.isFile) {
            Log.w(TAG, "share source missing: ${source.path}")
            toast(context, "文件不存在，无法分享")
            return null
        }
        val dir = shareDir(context)
        if (source.parentFile?.canonicalPath == dir.canonicalPath) return source
        val safeName = displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { source.name }
        // 每次分享一个子目录：接收方看到的是原文件名，又不会和上一次分享的同名文件冲突
        val target = File(File(dir, System.currentTimeMillis().toString()).apply { mkdirs() }, safeName)
        return try {
            source.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            target
        } catch (e: Exception) {
            Log.e(TAG, "copy for share failed: ${source.path}", e)
            toast(context, "分享失败")
            null
        }
    }

    /** `cache/share/`；顺手清掉一天前的分享副本。 */
    private fun shareDir(context: Context): File {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        val expired = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        dir.listFiles()?.filter { it.lastModified() < expired }?.forEach { it.deleteRecursively() }
        return dir
    }

    /** 优先用应用内分享面板；拿不到 Activity 时才退回系统选择器。 */
    private fun start(context: Context, intent: Intent, uri: Uri?, chooserTitle: String?): Boolean {
        val shown = try {
            ShareSheet.show(context, intent, uri, chooserTitle)
        } catch (e: Exception) {
            Log.e(TAG, "show share sheet failed", e)
            false
        }
        if (shown) return true
        return try {
            // 权限和 ClipData 必须同时挂在外层的选择器 Intent 上，否则目标应用读不到文件
            val chooser = Intent.createChooser(intent, chooserTitle).apply {
                clipData = intent.clipData
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Log.e(TAG, "share failed", e)
            toast(context, "没有可以分享的应用")
            false
        }
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    internal fun toast(context: Context, message: String) {
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
    }
}
