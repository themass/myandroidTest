package com.openapi.feature.companion.fx

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 贴纸词表跨模块一致性的钉子。
 *
 * `:feature-companion` 与 `:feature-camera-fx` **不建依赖边**（feature 互不依赖纪律，
 * 见 feature-companion/build.gradle 与 CameraGestureCue 注释），所以面板那一侧的贴纸 id
 * 是字面镜像。镜像漂移的后果很难看：面板可点 → 相机侧 `require(id in ALLOWED)` 抛，
 * 或者反过来「面板上没有但相机能用」。这里用扫源码的方式钉住两边同一组 id。
 */
class StickerPanelMirrorTest {

    private val repoRoot: File by lazy {
        var dir = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "settings.gradle").isFile) {
            dir = dir.parentFile
        }
        checkNotNull(dir) { "cannot locate repo root from ${System.getProperty("user.dir")}" }
    }

    /** 从 Kotlin 源码里抠 `const val X = "id"` 的字面值（按对象名限定范围）。 */
    private fun constStringsIn(file: File, objectName: String): Set<String> {
        val text = file.readText()
        val start = text.indexOf("object $objectName")
        assertTrue("找不到 object $objectName in $file", start >= 0)
        val body = text.substring(start)
        return Regex("""const\s+val\s+\w+\s*=\s*"([^"]+)"""")
            .findAll(body)
            .map { it.groupValues[1] }
            .toSet()
    }

    @Test
    fun `panel sticker ids mirror camera sticker whitelist`() {
        val panel = constStringsIn(
            File(repoRoot, "feature-companion/src/main/java/com/openapi/feature/companion/fx/StageFxOptions.kt"),
            "CameraStickers",
        )
        val camera = constStringsIn(
            File(repoRoot, "feature-camera-fx/src/main/java/com/openapi/feature/camerafx/CameraFxOverlayPolicy.kt"),
            "StickerAssetIds",
        )
        assertTrue("面板贴纸 $panel 与相机贴纸白名单 $camera 不一致", panel == camera)
    }

    @Test
    fun `camera sticker assets exist on both sides of the module boundary`() {
        // 面板能选 → 相机模块的 assets 里必须有 json，否则运行时永远「加载…」。
        val cameraAssets = File(repoRoot, "feature-camera-fx/src/main/assets/fx")
        assertTrue("找不到 $cameraAssets", cameraAssets.isDirectory)
        val camera = constStringsIn(
            File(repoRoot, "feature-camera-fx/src/main/java/com/openapi/feature/camerafx/CameraFxOverlayPolicy.kt"),
            "StickerAssetIds",
        )
        val missing = camera.filter { !File(cameraAssets, "$it.json").isFile }
        assertTrue("相机模块缺贴纸资产：$missing", missing.isEmpty())
    }

    @Test
    fun `stage effect words are not offered as stickers`() {
        // 用户 2026-11-28：「贴纸只是挪用其中 3 个」——特效词不得再混进贴纸列。
        val stageWords = listOf("fx_wave", "fx_swipe", "fx_connected")
        val panelUser = StageFxOptions.options(FxTarget.USER, cameraGranted = true).map { it.assetId }
        val leaked = stageWords.filter { it in panelUser }
        assertTrue("特效词漏进贴纸列：$leaked", leaked.isEmpty())
    }
}
