package com.openapi.myapp.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.openapi.common.util.PreferenceUtils
import com.openapi.ks.free1.R
import com.openapi.myapp.base.MyApplication
import com.openapi.myapp.constant.Constants
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * 应用分享：渲染一张深色分享卡（应用名、标语、二维码、下载链接），
 * 通过系统分享面板发出（微信、Telegram、短信等都在里面，不需要逐个接入 SDK）。
 *
 * 渲染用 Android Canvas 而不是界面截图：结果稳定、不依赖页面是否可见，可以在后台线程完成。
 */
object ShareCard {

    private const val WIDTH = 1080
    private const val HEIGHT = 1440
    private const val QR_SIZE = 620
    private const val BG_TOP = "#2A2740"
    private const val BG_BOTTOM = "#1C1B24"
    private const val ACCENT = "#6D4AFF"
    private const val TEXT = "#E8E8EE"
    private const val TEXT_SECONDARY = "#A7A4B5"
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 分享入口：后台渲染分享图，完成后打开系统分享面板。
     * inviteCode 不为空时，图片上会印出邀请码，被邀请人注册时填写即可。
     */
    @JvmStatic
    @JvmOverloads
    fun share(context: Context, inviteCode: String? = null) {
        val appContext = context.applicationContext
        val link = shareLink()
        executor.execute {
            val uri = try {
                val file = writeImage(appContext, render(appContext, link, inviteCode))
                FileProvider.getUriForFile(appContext, appContext.packageName + ".share", file)
            } catch (e: Exception) {
                null
            }
            mainHandler.post { startChooser(context, link, uri) }
        }
    }

    /** 分享链接：优先用服务端下发的地址，否则用默认官网。 */
    private fun shareLink(): String {
        val url = PreferenceUtils.getPrefString(MyApplication.getInstance(), Constants.D_URL, null)
        return if (url.isNullOrBlank()) Constants.DEFAULT_REFERER else url
    }

    private fun startChooser(context: Context, link: String, uri: Uri?) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text, link))
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_title))
            if (uri != null) {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_chooser)))
    }

    private fun render(context: Context, link: String, inviteCode: String?): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bg = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, HEIGHT.toFloat(),
                Color.parseColor(BG_TOP), Color.parseColor(BG_BOTTOM), Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), bg)

        val title = text(TEXT, 96f, bold = true)
        val tagline = text(TEXT_SECONDARY, 46f)
        val small = text(TEXT_SECONDARY, 40f)
        val accent = text(ACCENT, 44f, bold = true)

        canvas.drawText(context.getString(R.string.app_name), WIDTH / 2f, 220f, title)
        canvas.drawText(context.getString(R.string.share_tagline), WIDTH / 2f, 300f, tagline)

        // 二维码放在白色圆角底上，保证扫码对比度
        val qrLeft = (WIDTH - QR_SIZE) / 2f
        val qrTop = 430f
        val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRoundRect(
            qrLeft - 40f, qrTop - 40f, qrLeft + QR_SIZE + 40f, qrTop + QR_SIZE + 40f, 48f, 48f, panel,
        )
        canvas.drawBitmap(qrBitmap(link, QR_SIZE), qrLeft, qrTop, null)

        canvas.drawText(context.getString(R.string.share_scan), WIDTH / 2f, qrTop + QR_SIZE + 140f, accent)
        var linkY = qrTop + QR_SIZE + 210f
        val code = inviteCode?.takeIf { it.isNotBlank() }
        if (code != null) {
            canvas.drawText(context.getString(R.string.share_invite_code, code), WIDTH / 2f, qrTop + QR_SIZE + 210f, accent)
            canvas.drawText(context.getString(R.string.share_invite_hint), WIDTH / 2f, qrTop + QR_SIZE + 270f, small)
            linkY = qrTop + QR_SIZE + 330f
        }
        canvas.drawText(link, WIDTH / 2f, linkY, small)
        return bitmap
    }

    private fun text(color: String, size: Float, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.parseColor(color)
            textSize = size
            textAlign = Paint.Align.CENTER
            if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

    private fun qrBitmap(content: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(
            content, BarcodeFormat.QR_CODE, size, size,
            mapOf(EncodeHintType.MARGIN to 1),
        )
        val pixels = IntArray(size * size) { i ->
            if (matrix.get(i % size, i / size)) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    private fun writeImage(context: Context, bitmap: Bitmap): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "shieldwing-share.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }
}
