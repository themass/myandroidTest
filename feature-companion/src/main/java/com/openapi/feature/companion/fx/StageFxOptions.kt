package com.openapi.feature.companion.fx

/**
 * 特效面板的可测决策层（用户 2026-11-27：「区分特效和 stiker……区分给 her 使用还是用户的」）。
 *
 * 面板只列白名单资产，且按目标分组：
 * - [FxTarget.HER]：画在角色舞台上的叠加层，走既有 gesture 词表（cmp.cue 同路）；
 * - [FxTarget.USER]：贴在**本机相机画面**上的贴纸，依赖相机权限与人脸跟踪。
 *
 * 不可用的组合不隐藏、也不假装可点：[FxOption.enabled]=false 时给出
 * [FxOption.unavailableReason]，由 UI 置灰并显示原因。
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
        FxAssets.PAT_HEAD to "摸头爱心",
        FxAssets.WAVE to "挥手光弧",
        FxAssets.CONNECTED to "接通花瓣",
        FxAssets.SWIPE to "滑动流光",
    )

    /** 资产 → gesture 词表 id（与 [StageEffectMapper.fromGesture] 的别名对齐）。 */
    private val gestureIds = mapOf(
        FxAssets.PAT_HEAD to "pat_head",
        FxAssets.WAVE to "wave",
        FxAssets.CONNECTED to StageEffectMapper.GESTURE_CONNECTED,
        FxAssets.SWIPE to "swipe",
    )

    /**
     * 相机贴纸白名单：与 feature-camera-fx 的 StickerAssetIds.ALLOWED 一致。
     * 只保证头顶锚点，不做任意部位绑定，因此不列 FX_CONNECTED（全屏氛围不属于贴纸）。
     */
    private val cameraStickerAssets = listOf(FxAssets.PAT_HEAD, FxAssets.WAVE, FxAssets.SWIPE)

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
                gestureId = gestureIds[spec.assetId],
                enabled = !reducedMotion && (particles || spec.category != FxCategory.AMBIENCE),
                unavailableReason = when {
                    reducedMotion -> "系统已开启减弱动态效果"
                    particles.not() && spec.category == FxCategory.AMBIENCE -> "已关闭氛围粒子"
                    else -> null
                },
            )
        }

        FxTarget.USER -> cameraStickerAssets.map { assetId ->
            FxOption(
                assetId = assetId,
                label = labels[assetId] ?: assetId,
                target = target,
                category = FxAssets.lookup(assetId)?.category ?: FxCategory.EVENT_RESPONSE,
                gestureId = null,
                enabled = cameraGranted,
                unavailableReason = if (cameraGranted) null else "需要相机权限",
            )
        }
    }
}
