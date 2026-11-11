package com.aimath.paizhao.photosolve.image


import com.openapi.imaging.image.TileGeometry
import com.openapi.imaging.image.TileGeometry.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileGeometryTest {

    /** 有效区域必须正好铺满整图：面积之和等于图像面积，且两两不重叠。 */
    private fun assertCoverageExact(width: Int, height: Int, tiles: List<Tile>) {
        val covered = Array(height) { BooleanArray(width) }
        for (t in tiles) {
            for (y in t.coreTop until t.coreTop + t.coreHeight) {
                for (x in t.coreLeft until t.coreLeft + t.coreWidth) {
                    assertTrue("core pixel ($x,$y) covered twice", !covered[y][x])
                    covered[y][x] = true
                }
            }
        }
        for (y in 0 until height) for (x in 0 until width) {
            assertTrue("pixel ($x,$y) not covered", covered[y][x])
        }
    }

    /** 输入区域必须落在原图内，且包含对应的有效区域。 */
    private fun assertInputContainsCore(width: Int, height: Int, tiles: List<Tile>) {
        for (t in tiles) {
            assertTrue(t.srcLeft >= 0 && t.srcTop >= 0)
            assertTrue(t.srcLeft + t.srcWidth <= width)
            assertTrue(t.srcTop + t.srcHeight <= height)
            assertTrue(t.srcLeft <= t.coreLeft && t.srcTop <= t.coreTop)
            assertTrue(t.srcLeft + t.srcWidth >= t.coreLeft + t.coreWidth)
            assertTrue(t.srcTop + t.srcHeight >= t.coreTop + t.coreHeight)
        }
    }

    @Test
    fun coversExactlyWithoutOverlapForAwkwardSize() {
        val tiles = TileGeometry.plan(width = 1000, height = 730, tileSize = 128, overlap = 16)
        assertCoverageExact(1000, 730, tiles)
        assertInputContainsCore(1000, 730, tiles)
    }

    @Test
    fun imageSmallerThanOneTileIsSingleTile() {
        val tiles = TileGeometry.plan(width = 50, height = 30, tileSize = 128, overlap = 16)
        assertEquals(1, tiles.size)
        assertCoverageExact(50, 30, tiles)
    }

    @Test
    fun interiorTilesKeepFullContext() {
        val tiles = TileGeometry.plan(width = 512, height = 512, tileSize = 128, overlap = 16)
        val interior = tiles.first { it.coreLeft > 0 && it.coreTop > 0 && it.coreLeft + it.coreWidth < 512 && it.coreTop + it.coreHeight < 512 }
        assertEquals(interior.coreLeft - 16, interior.srcLeft)
        assertEquals(interior.coreTop - 16, interior.srcTop)
        assertEquals(interior.coreWidth + 32, interior.srcWidth)
        assertEquals(interior.coreHeight + 32, interior.srcHeight)
    }

    @Test
    fun rejectsOverlapThatLeavesNoCore() {
        var threw = false
        try {
            TileGeometry.plan(100, 100, tileSize = 32, overlap = 16)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun exactMultipleOfStepHasNoPartialTile() {
        val step = 128 - 2 * 16
        val tiles = TileGeometry.plan(width = step * 3, height = step * 2, tileSize = 128, overlap = 16)
        assertEquals(6, tiles.size)
        assertCoverageExact(step * 3, step * 2, tiles)
    }
}
