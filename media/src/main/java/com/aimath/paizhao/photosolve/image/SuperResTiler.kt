package com.aimath.paizhao.photosolve.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.aimath.paizhao.photosolve.image.TileGeometry.Tile

/**
 * 把大图切成固定大小的块做超分，再拼回去。
 *
 * 超分模型的输入尺寸有上限，整图一次推理会 OOM。切块的几何见 [TileGeometry]；
 * 这里只负责 Bitmap 的裁剪与拼接。
 */
object SuperResTiler {

    fun plan(width: Int, height: Int, tileSize: Int, overlap: Int): List<Tile> =
        TileGeometry.plan(width, height, tileSize, overlap)

    /** 按方案切出一块的输入图像。 */
    fun cropTile(source: Bitmap, tile: Tile): Bitmap =
        Bitmap.createBitmap(source, tile.srcLeft, tile.srcTop, tile.srcWidth, tile.srcHeight)

    /**
     * 把每块的超分结果拼回一张大图。只取每块的有效区域（去掉四周的上下文），
     * 所以块与块之间没有重叠，接缝由几何保证对齐。
     *
     * @param results 与 [plan] 一一对应的超分结果；每块大小应为 srcWidth*scale × srcHeight*scale
     */
    fun stitch(
        results: List<Pair<Tile, Bitmap>>,
        width: Int,
        height: Int,
        scale: Int,
    ): Bitmap {
        val out = Bitmap.createBitmap(width * scale, height * scale, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        for ((tile, upscaled) in results) {
            val leftPad = (tile.coreLeft - tile.srcLeft) * scale
            val topPad = (tile.coreTop - tile.srcTop) * scale
            val coreW = tile.coreWidth * scale
            val coreH = tile.coreHeight * scale
            val dstLeft = tile.coreLeft * scale
            val dstTop = tile.coreTop * scale
            canvas.drawBitmap(
                upscaled,
                Rect(leftPad, topPad, leftPad + coreW, topPad + coreH),
                Rect(dstLeft, dstTop, dstLeft + coreW, dstTop + coreH),
                paint,
            )
        }
        return out
    }
}
