package com.openapi.feature.video.screening

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 图集封面瀑布流的列分配（台账 A27）。
 *
 * 旧 View 版图集（`ImgChannleBodyFragment` 那条链）网格 span 是 3，Compose 迁移时手写
 * 瀑布流硬编码成 2 列，同页其它列表又是 3 列（`AlbumContents` 用 `chunked(3)`），
 * 三处不一致，用户点名「图集封面怎么只有两个一行的」。
 * 钉四条：列数就是 3；列内保持服务端返回顺序；每列拿到该拿的下标；任意数量下
 * 三列加起来既不重也不漏（漏一个封面就是少一本图集，重复就是同一本画两遍）。
 */
class AlbumCoverColumnsTest {

    @Test
    fun `album cover waterfall is three columns like the legacy grid`() {
        assertEquals(3, ALBUM_COVER_COLUMNS)
        // 首屏前 3 个封面必须一行放得下：下标 0/1/2 落在三个不同的列
        assertEquals(listOf(0, 1, 2), (0..2).map { albumCoverColumn(it) })
    }

    @Test
    fun `nine covers spread three per column in server order`() {
        val keys = (1..9).map { "album$it" }
        val expected = listOf(
            listOf("album1", "album4", "album7"),
            listOf("album2", "album5", "album8"),
            listOf("album3", "album6", "album9"),
        )
        assertEquals(expected, (0 until ALBUM_COVER_COLUMNS).map { albumCoverColumnItems(keys, it) })
        assertEquals(listOf(3, 3, 3), (0 until ALBUM_COVER_COLUMNS).map { albumCoverColumnItems(keys, it).size })
    }

    @Test
    fun `non multiple of three leaves the tail in the leading columns`() {
        // 7 本：第 7 本下标 6，6 % 3 == 0 → 回到第一列，不是塞进最后一列补齐
        val keys = (1..7).map { "album$it" }
        assertEquals(
            listOf(
                listOf("album1", "album4", "album7"),
                listOf("album2", "album5"),
                listOf("album3", "album6"),
            ),
            (0 until ALBUM_COVER_COLUMNS).map { albumCoverColumnItems(keys, it) },
        )
        assertEquals(listOf(3, 2, 2), (0 until ALBUM_COVER_COLUMNS).map { albumCoverColumnItems(keys, it).size })
    }

    @Test
    fun `columns are a partition - no cover lost, none drawn twice`() {
        for (size in 0..23) {
            val keys = (0 until size).map { it.toString() }
            val flattened = (0 until ALBUM_COVER_COLUMNS).flatMap { albumCoverColumnItems(keys, it) }
            assertEquals("size=$size 总数", size, flattened.size)
            assertEquals("size=$size 无重复", keys.toSet(), flattened.toSet())
            // 每列拿到的就是「本列下标上的那批」，且保持服务端顺序：
            // 第 c 列依次是下标 c、c+3、c+6…，列内不能乱序、不能跨列抓。
            for (column in 0 until ALBUM_COVER_COLUMNS) {
                val expectedByIndex = (column until size step ALBUM_COVER_COLUMNS).map { keys[it] }
                assertEquals("size=$size 第 $column 列", expectedByIndex, albumCoverColumnItems(keys, column))
            }
        }
    }

    @Test
    fun `album contents grid stays three across`() {
        // AlbumContents 用的是 chunked(3)：封面列数和网格列数必须同一个数，别一边改一边漏
        val shots = (1..7).map { "shot$it" }
        assertEquals(3, shots.chunked(3).first().size)
        assertEquals(listOf(3, 3, 1), shots.chunked(3).map { it.size })
        assertEquals(ALBUM_COVER_COLUMNS, shots.chunked(3).first().size)
    }
}
