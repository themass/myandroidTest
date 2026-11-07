package com.aimath.paizhao.photosolve.image

/** 切块几何：纯整数运算，不依赖 Android 类，便于在 JVM 上单测。 */
object TileGeometry {

    data class Tile(
        /** 在原图中的位置（含重叠区），用于裁剪输入。 */
        val srcLeft: Int,
        val srcTop: Int,
        val srcWidth: Int,
        val srcHeight: Int,
        /** 有效区域（去掉重叠后的中心）在原图中的位置。 */
        val coreLeft: Int,
        val coreTop: Int,
        val coreWidth: Int,
        val coreHeight: Int,
    )

    /**
     * 计算切块方案。相邻有效区域首尾相接、互不重叠、覆盖整图；
     * 每块的输入区域在有效区域四周再加 [overlap]，但不超出原图。
     *
     * @param tileSize 每块输入（含重叠）的最大边长，像素
     * @param overlap 有效区域四周额外取的上下文，像素
     */
    fun plan(width: Int, height: Int, tileSize: Int, overlap: Int): List<Tile> {
        require(width > 0 && height > 0) { "image must be non-empty" }
        require(tileSize > 2 * overlap) { "tileSize must exceed 2*overlap" }
        val step = tileSize - 2 * overlap
        val tiles = ArrayList<Tile>()
        var y = 0
        while (y < height) {
            val coreH = minOf(step, height - y)
            var x = 0
            while (x < width) {
                val coreW = minOf(step, width - x)
                val srcLeft = (x - overlap).coerceAtLeast(0)
                val srcTop = (y - overlap).coerceAtLeast(0)
                val srcRight = (x + coreW + overlap).coerceAtMost(width)
                val srcBottom = (y + coreH + overlap).coerceAtMost(height)
                tiles += Tile(
                    srcLeft = srcLeft,
                    srcTop = srcTop,
                    srcWidth = srcRight - srcLeft,
                    srcHeight = srcBottom - srcTop,
                    coreLeft = x,
                    coreTop = y,
                    coreWidth = coreW,
                    coreHeight = coreH,
                )
                x += step
            }
            y += step
        }
        return tiles
    }
}
