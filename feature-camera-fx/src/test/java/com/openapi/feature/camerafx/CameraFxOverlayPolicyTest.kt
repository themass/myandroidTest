package com.openapi.feature.camerafx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraFxOverlayPolicyTest {

    @Test
    fun lottieAssetPath() {
        val p = CameraFxOverlayPolicy(stickerAssetId = StickerAssetIds.WAVE)
        assertEquals("fx/fx_wave.json", p.lottieAssetPath)
    }

    @Test
    fun everyAllowedAssetExistsOnDisk() {
        // 词表加了 id 但没把 JSON 拷进本模块 assets → 运行时永远「加载…」。
        // companion 与本模块各有一份（内容相同），两边都要有——这里钉本模块。
        val dir = java.io.File("src/main/assets/fx")
        assertTrue("找不到 $dir.absolutePath", dir.isDirectory)
        for (id in StickerAssetIds.ALLOWED) {
            assertTrue("缺资产：$id", java.io.File(dir, "$id.json").isFile)
        }
    }
}
