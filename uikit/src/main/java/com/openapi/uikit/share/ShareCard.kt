package com.openapi.uikit.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.common.BitMatrix

/**
 * 分享卡片：深色底、标题、副标题、二维码和链接，渲染成位图后按图片分享。
 * 渲染用 Canvas 而不是页面截图，结果稳定，可以在后台线程完成。
 */
object ShareCard {
    private const val WIDTH = 1080
    private const val HEIGHT = 1440
    private const val QR_SIZE = 620

    fun render(context: Context, title: String, subtitle: String, link: String): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, HEIGHT.toFloat(), Color.parseColor("#2A2740"), Color.parseColor("#1C1B24"), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), bg)

        val title0 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E8E8EE"); textSize = 120f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A7A4B5"); textSize = 56f; textAlign = Paint.Align.CENTER
        }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A7A4B5"); textSize = 40f; textAlign = Paint.Align.CENTER
        }
        canvas.drawText(title, WIDTH / 2f, 330f, title0)
        canvas.drawText(subtitle, WIDTH / 2f, 430f, sub)

        val qr = qrBitmap(link, QR_SIZE)
        val left = (WIDTH - QR_SIZE) / 2f
        val top = 560f
        val frame = Paint().apply { color = Color.WHITE }
        canvas.drawRoundRect(left - 24, top - 24, left + QR_SIZE + 24, top + QR_SIZE + 24, 32f, 32f, frame)
        canvas.drawBitmap(qr, left, top, null)

        canvas.drawText(link, WIDTH / 2f, top + QR_SIZE + 130f, small)
        return bitmap
    }

    private fun qrBitmap(content: String, size: Int): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix: BitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) for (y in 0 until size) {
            bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
        }
        return bmp
    }
}
