package com.openapi.chatkit.message.parts

import com.openapi.chatkit.model.ImageLayout
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 图片块排布决策（[imageArrangement]）的分支表测试。
 *
 * 「默认一律收纳盒」是聊天与陪伴端样式统一的关键约定（设计 §4.2），张数不再决定样式；
 * 旧的「按张数分支」保留在 [ImageLayout.Auto] 里。纯 JVM 覆盖，不依赖 Compose。
 */
class ImageArrangementTest {
    @Test
    fun `Stack 策略下任意张数都进收纳盒`() {
        for (count in listOf(1, 2, 4, 7, 9)) {
            assertEquals(ImageArrangement.Stack, imageArrangement(count, ImageLayout.Stack))
        }
    }

    @Test
    fun `Auto 策略保留旧分支`() {
        assertEquals(ImageArrangement.SingleFit, imageArrangement(1, ImageLayout.Auto))
        assertEquals(ImageArrangement.Stack, imageArrangement(2, ImageLayout.Auto))
        assertEquals(ImageArrangement.Stack, imageArrangement(4, ImageLayout.Auto))
        assertEquals(ImageArrangement.Carousel, imageArrangement(5, ImageLayout.Auto))
        assertEquals(ImageArrangement.Carousel, imageArrangement(9, ImageLayout.Auto))
    }
}
