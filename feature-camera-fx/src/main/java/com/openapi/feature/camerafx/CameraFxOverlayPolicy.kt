package com.openapi.feature.camerafx

/**
 * 相机预览上的叠加策略（纯数据，JVM 可测）。
 *
 * 与舞台 [com.openapi.feature.companion.fx.StageEffect] 解耦：相机用像素/CanvasMap，
 * 舞台用视口 Norm。
 *
 * **贴纸词表与特效词表是两套**（用户 2026-11-28：「贴纸只是挪用其中 3 个……贴纸你也没搞啊」）：
 * 舞台特效是对「发生了什么」的反馈（光弧、流光），贴纸是贴在人身上某个部位的装饰。
 * 因此这里不再镜像 `fx_wave` / `fx_swipe`，而是自己的 `sticker_*` 一组，按部位分：
 * 头顶 / 眼睛 / 嘴 / 手。部位决定要哪个跟踪信号（见 [StickerAssetIds.needsFace]）。
 */
data class CameraFxOverlayPolicy(
    val drawWireframe: Boolean = true,
    val drawStickers: Boolean = true,
    /** `assets/fx/<id>.json`，须在 [StickerAssetIds.ALLOWED] 内。 */
    val stickerAssetId: String = StickerAssetIds.PAT_HEAD,
    val showDebugHud: Boolean = true,
) {
    init {
        require(stickerAssetId in StickerAssetIds.ALLOWED) {
            "unknown sticker asset: $stickerAssetId"
        }
    }

    val lottieAssetPath: String get() = "fx/$stickerAssetId.json"
}

/** 贴纸挂哪个部位——决定它需要脸还是手的跟踪结果。 */
enum class StickerAnchorPart {
    /** 头顶锚点（[headTopAnchor]）：头饰类。 */
    HEAD_TOP,

    /** 两眼连线中点：墨镜类（宽度按眼距，不按脸宽）。 */
    EYES,

    /** 外唇 landmark 均值：胡子类。 */
    MOUTH,

    /** 手掌中心（21 点均值）：手持类。 */
    HAND,
}

/**
 * 贴纸白名单（本模块唯一事实源；companion 面板侧的镜像由
 * `StageFxOptions`+`StickerPanelMirrorTest` 钉住一致）。
 *
 * `fx_pat_head` 是历史 id（通话页默认贴纸），保留原名以免破坏既有偏好与调用点；
 * 新增贴纸一律 `sticker_*` 前缀，与舞台特效词表在文件名上就可区分。
 */
object StickerAssetIds {
    const val PAT_HEAD = "fx_pat_head"
    const val CAT_EARS = "sticker_cat_ears"
    const val CROWN = "sticker_crown"
    const val FLOWER_WREATH = "sticker_flower_wreath"
    const val SUNGLASSES = "sticker_sunglasses"
    const val MUSTACHE = "sticker_mustache"
    const val HAND_HEART = "sticker_hand_heart"

    data class Spec(
        val assetId: String,
        val part: StickerAnchorPart,
        /** lab 芯片用的短标签（面板长文案在 companion 的 StageFxOptions，两边各管各的界面）。 */
        val label: String,
    )

    private val registered = listOf(
        Spec(PAT_HEAD, StickerAnchorPart.HEAD_TOP, "爱心"),
        Spec(CAT_EARS, StickerAnchorPart.HEAD_TOP, "猫耳"),
        Spec(CROWN, StickerAnchorPart.HEAD_TOP, "皇冠"),
        Spec(FLOWER_WREATH, StickerAnchorPart.HEAD_TOP, "花环"),
        Spec(SUNGLASSES, StickerAnchorPart.EYES, "墨镜"),
        Spec(MUSTACHE, StickerAnchorPart.MOUTH, "胡子"),
        Spec(HAND_HEART, StickerAnchorPart.HAND, "比心"),
    )

    val all: List<Spec> get() = registered

    val ALLOWED: Set<String> = registered.map { it.assetId }.toSet()

    fun lookup(assetId: String): Spec? = registered.firstOrNull { it.assetId == assetId }

    /** 需要人脸跟踪（头顶/眼/嘴三类）。 */
    fun needsFace(assetId: String): Boolean =
        lookup(assetId)?.part?.let { it != StickerAnchorPart.HAND } ?: false

    /** 需要手部跟踪（手持类）。 */
    fun needsHand(assetId: String): Boolean =
        lookup(assetId)?.part == StickerAnchorPart.HAND

    fun assetPath(assetId: String): String = "fx/$assetId.json"
}

/**
 * 贴纸此刻能不能画（纯函数，JVM 单测钉死）。
 *
 * 面板与相机预览共用这套原因文案：不可用**不隐藏**，置灰并说清缺什么
 * （用户 2026-11-27「先展示出来，我来点击测试效果」）。相机侧的判定按帧来：
 * 脸在不在画面、手在不在画面，与权限无关（权限缺失在面板那一层已经置灰）。
 */
object StickerAvailability {

    data class Verdict(val available: Boolean, val reason: String?)

    fun check(
        stickerAssetId: String,
        hasFace: Boolean,
        hasHand: Boolean,
    ): Verdict {
        val spec = lookupSticker(stickerAssetId)
            ?: return Verdict(false, "贴纸不在白名单")
        return when {
            spec.part == StickerAnchorPart.HAND && !hasHand -> Verdict(false, "把手放进画面")
            spec.part != StickerAnchorPart.HAND && !hasFace -> Verdict(false, "未识别人脸：正对镜头")
            else -> Verdict(true, null)
        }
    }

    private fun lookupSticker(assetId: String) = StickerAssetIds.lookup(assetId)
}
