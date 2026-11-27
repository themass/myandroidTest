package com.openapi.feature.camerafx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraFxOverlayPolicyTest {

    @Test
    fun lottieAssetPath() {
        val p = CameraFxOverlayPolicy(stickerAssetId = StickerAssetIds.CROWN)
        assertEquals("fx/sticker_crown.json", p.lottieAssetPath)
    }

    @Test
    fun stageEffectWordsAreNotAcceptedAsStickers() {
        // 贴纸白名单不再挪用舞台特效词（fx_wave / fx_swipe）：传进来必须抛，
        // 而不是静默画一张舞台素材。
        for (borrowed in listOf("fx_wave", "fx_swipe", "fx_connected")) {
            val thrown = runCatching { CameraFxOverlayPolicy(stickerAssetId = borrowed) }.isFailure
            assertTrue("$borrowed 不该再当贴纸", thrown)
        }
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
