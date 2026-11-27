package com.openapi.feature.companion.fx

/**
 * 特效面板的可测决策层（用户 2026-11-27：「区分特效和 stiker……区分给 her 使用还是用户的」；
 * 2026-11-28 追加：「特效只有 4 个……贴纸你也没搞啊」）。
 *
 * 面板只列白名单资产，且按目标分组：
 * - [FxTarget.HER]：画在角色舞台上的叠加层，走既有 gesture 词表（cmp.cue 同路）；
 * - [FxTarget.USER]：贴在**本机相机画面**上的贴纸，依赖相机权限与人脸/手跟踪。
 *
 * 不可用的组合不隐藏、也不假装可点：[FxOption.enabled]=false 时给出
 * [FxOption.unavailableReason]，由 UI 置灰并显示原因。
 *
 * 词表跟着白名单走：HER 侧直接读 [FxAssets.all]（加一行就进面板），USER 侧是
 * [cameraStickerAssets]——feature 之间不建依赖边，所以那份 id 是从 `feature-camera-fx`
 * 的 `StickerAssetIds.ALLOWED` **镜像**过来的，由 [StickerPanelMirrorTest] 扫源码钉住
 * 两边一致（漂移 = 面板可点但相机侧 require 抛，是这里最贵的错）。
 */
enum class FxTarget { HER, USER }

data class FxOption(
    val assetId: String,
    /** 面板文案。 */
    val label: String,
    val target: FxTarget,
    val category: FxCategory,
    /** HER 用：点击时写进既有 gesture 词表的 id；USER 不发送舞台 cue。 */
    val gestureId: String?,
    val enabled: Boolean,
    val unavailableReason: String?,
)

/** 面板选项的唯一来源。纯函数，JVM 单测钉死。 */
object StageFxOptions {

    private val labels = mapOf(
        // HER·既有
        FxAssets.PAT_HEAD to "摸头爱心",
        FxAssets.WAVE to "挥手光弧",
        FxAssets.CONNECTED to "接通花瓣",
        FxAssets.SWIPE to "滑动流光",
        // HER·头部
        FxAssets.CAT_EARS to "猫耳",
        FxAssets.STAR_EYES to "星星眼",
        FxAssets.BLUSH to "脸红",
        // HER·身体
        FxAssets.BUBBLES to "气泡",
        FxAssets.HALO to "光环",
        // HER·手部
        FxAssets.SPARKLE to "指尖闪光",
        // HER·全屏事件
        FxAssets.FIREWORKS to "烟花",
        FxAssets.COINS to "金币雨",
        // HER·全屏氛围
        FxAssets.HEART_RAIN to "爱心雨",
        FxAssets.SNOW to "雪花飘落",
        FxAssets.RAIN to "雨滴",
        FxAssets.SPOTLIGHT to "追光",
        // USER·相机贴纸
        CameraStickers.PAT_HEAD to "爱心",
        CameraStickers.CAT_EARS to "猫耳贴纸",
        CameraStickers.CROWN to "皇冠",
        CameraStickers.FLOWER_WREATH to "花环",
        CameraStickers.SUNGLASSES to "墨镜",
        CameraStickers.MUSTACHE to "胡子",
        CameraStickers.HAND_HEART to "手持比心",
    )

    /** 资产 → gesture 词表 id（与 [StageEffectMapper] 的别名表同源，取主词）。 */
    private fun gestureIdFor(assetId: String): String? = StageEffectMapper.primaryGesture(assetId)

    /** 面板 label 查询（HER/USER 同一张表）。 */
    fun labelFor(assetId: String): String = labels[assetId] ?: assetId

    /**
     * 相机贴纸白名单镜像（唯一事实源在 `feature-camera-fx` 的 `StickerAssetIds.ALLOWED`；
     * 见 [StickerPanelMirrorTest]）。只列真正的贴纸：舞台特效词（光弧/流光）**不**进这一列——
     * 用户 2026-11-28「贴纸只是挪用其中 3 个」的纠正。
     */
    private val cameraStickerAssets = listOf(
        CameraStickers.PAT_HEAD,
        CameraStickers.CAT_EARS,
        CameraStickers.CROWN,
        CameraStickers.FLOWER_WREATH,
        CameraStickers.SUNGLASSES,
        CameraStickers.MUSTACHE,
        CameraStickers.HAND_HEART,
    )

    fun options(
        target: FxTarget,
        cameraGranted: Boolean = false,
        particles: Boolean = true,
        reducedMotion: Boolean = false,
    ): List<FxOption> = when (target) {
        FxTarget.HER -> FxAssets.all.map { spec ->
            FxOption(
                assetId = spec.assetId,
                label = labels[spec.assetId] ?: spec.assetId,
                target = target,
                category = spec.category,
                gestureId = gestureIdFor(spec.assetId),
                enabled = !reducedMotion && (particles || spec.category != FxCategory.AMBIENCE),
                unavailableReason = when {
                    reducedMotion -> "系统已开启减弱动态效果"
                    particles.not() && spec.category == FxCategory.AMBIENCE -> "已关闭氛围粒子"
                    else -> null
                },
            )
        }

        // 贴纸是用户主动选的装饰，不受 particles/reducedMotion 影响（相机预览本身不是动画降级对象）；
        // 缺的是相机权限——整区置灰给原因，不隐藏。
        FxTarget.USER -> cameraStickerAssets.map { assetId ->
            FxOption(
                assetId = assetId,
                label = labels[assetId] ?: assetId,
                target = target,
                category = FxCategory.EVENT_RESPONSE,
                gestureId = null,
                enabled = cameraGranted,
                unavailableReason = if (cameraGranted) null else "需要相机权限",
            )
        }
    }
}

/**
 * `feature-camera-fx` 贴纸 id 的字面镜像（不建模块依赖边）。
 * 改这里必须同步改那边，[StickerPanelMirrorTest] 会红。
 */
object CameraStickers {
    const val PAT_HEAD = "fx_pat_head"
    const val CAT_EARS = "sticker_cat_ears"
    const val CROWN = "sticker_crown"
    const val FLOWER_WREATH = "sticker_flower_wreath"
    const val SUNGLASSES = "sticker_sunglasses"
    const val MUSTACHE = "sticker_mustache"
    const val HAND_HEART = "sticker_hand_heart"

    val ALL: List<String> = listOf(PAT_HEAD, CAT_EARS, CROWN, FLOWER_WREATH, SUNGLASSES, MUSTACHE, HAND_HEART)
}
