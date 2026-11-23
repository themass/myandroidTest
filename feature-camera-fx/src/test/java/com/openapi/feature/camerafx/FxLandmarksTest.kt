package com.openapi.feature.camerafx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 稀疏索引表语义钉桩。眉眼「交叉」真机缺陷的根因是绘制拓扑（共用 Path + 全 close），
 * 不是 MediaPipe 索引错——这里钉索引表本身的开/闭语义。
 */
class FxLandmarksTest {

    @Test
    fun `眉索引表是开折线首尾索引不同`() {
        assertEquals(5, FACE_BROW_LEFT.size)
        assertEquals(5, FACE_BROW_RIGHT.size)
        assertNotEquals(FACE_BROW_LEFT.first(), FACE_BROW_LEFT.last())
        assertNotEquals(FACE_BROW_RIGHT.first(), FACE_BROW_RIGHT.last())
    }

    @Test
    fun `眼与唇索引表首尾不同可闭合`() {
        assertNotEquals(FACE_EYE_LEFT.first(), FACE_EYE_LEFT.last())
        assertNotEquals(FACE_LIPS_OUTER.first(), FACE_LIPS_OUTER.last())
    }
}
