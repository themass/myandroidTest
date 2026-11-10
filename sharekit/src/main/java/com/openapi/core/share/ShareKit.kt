package com.openapi.core.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * 统一的分享入口：文字、文件、位图都走系统分享面板（[Intent.createChooser]）。
 *
 * - 文件通过 FileProvider 给出 content:// uri，并带上读取权限（ClipData + FLAG_GRANT_READ_URI_PERMISSION），
 *   接收方（微信、邮件等）才能读到文件；
 * - 位图先写到缓存目录 `share/`，再按文件分享；
 * - 失败时记日志并提示，不静默吞掉。
 */
object ShareKit {
    private const val TAG = "ShareKit"

    /** 分享文字（链接、说明等）。 */
    fun shareText(context: Context, text: String, subject: String? = null, chooserTitle: String? = null) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        start(context, intent, chooserTitle)
    }

    /**
     * 分享一个文件。[mimeType] 不传时按扩展名猜测，猜不到用 `application/octet-stream`。
     * 文件必须在 [sharekit_paths.xml] 开放的目录里，否则返回 false 并提示。
     */
    fun shareFile(
        context: Context,
        file: File,
        mimeType: String? = null,
        text: String? = null,
        chooserTitle: String? = null,
    ): Boolean {
        val uri = uriFor(context, file) ?: return false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType ?: guessMime(file.name)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (text != null) putExtra(Intent.EXTRA_TEXT, text)
        }
        return start(context, intent, chooserTitle)
    }

    /** 分享一张位图：写到 `share/` 缓存目录，再按 PNG 文件分享。 */
    fun shareBitmap(
        context: Context,
        bitmap: Bitmap,
        name: String = "share_${System.currentTimeMillis()}.png",
        text: String? = null,
        chooserTitle: String? = null,
    ): Boolean {
        val file = File(File(context.cacheDir, "share").apply { mkdirs() }, name)
        return try {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            shareFile(context, file, "image/png", text, chooserTitle)
        } catch (e: Exception) {
            Log.e(TAG, "write share bitmap failed", e)
            toast(context, "分享图片生成失败")
            false
        }
    }

    /**
     * 分享卡片（深色底 + 二维码 + 链接）：渲染成图片后按图片分享，文字里也带上链接，
     * 因为很多分享目标只收文字。渲染在后台线程完成。
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
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (bitmap == null) {
                    toast(app, "分享图片生成失败")
                    shareText(context, link, subject = title, chooserTitle = chooserTitle)
                } else {
                    shareBitmap(context, bitmap, text = link, chooserTitle = chooserTitle)
                }
            }
        }.start()
    }

    /** 文件对应的 content:// uri；不在开放目录里会返回 null。 */
    fun uriFor(context: Context, file: File): Uri? = try {
        FileProvider.getUriForFile(context, authority(context), file)
    } catch (e: IllegalArgumentException) {
        Log.e(TAG, "file not shareable: ${file.path}", e)
        toast(context, "无法分享这个文件")
        null
    }

    fun authority(context: Context): String = context.packageName + ".sharekit"

    private fun start(context: Context, intent: Intent, chooserTitle: String?): Boolean = try {
        // 权限和 ClipData 必须同时挂在外层的选择器 Intent 上，否则目标应用读不到文件（会显示「获取资源失败」）
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

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
    }
}
