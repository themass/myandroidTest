package com.openapi.feature.companion.fx

/**
 * 舞台特效层的唯一事件词汇（方案 lottie-fx-plan-20261123.md §4）。
 *
 * 特效层只认这个类型 + 视口归一化坐标：不认识协议（AvatarCommand/cmp.cue）、
 * 不认识引擎（无任何 dh 符号）。触发走既有 Gesture 词表
 * （cmp.cue → AvatarEventMapper → AvatarCommand.Gesture → uiState.gestureSeq），
 * [StageEffectMapper.fromGesture] 翻成本类型再播——**不另起事件总线**。
 *
 * [assetId] 必须在 [FxAssets] 白名单内；白名单外的 id 被 mapper 回 null，永不进渲染。
 */
sealed interface StageEffect {

    /** 视口锚点。归一化基准点与尺寸换算见 [FxAnchors]（fx 包内自足，不读引擎）。 */
    enum class Anchor { HEAD, BODY, HAND, FULL }

    /** 循环次数：1 = 播一遍；[LOOP_UNTIL_CANCELLED] = 氛围类循环。 */
    companion object {
        const val LOOP_UNTIL_CANCELLED = -1
    }

    data class Play(
        /** bundle 内 Lottie 资产 id（[FxAssets] 词表），不含扩展名。 */
        val assetId: String,
        val anchor: Anchor,
        val repeat: Int = 1,
    ) : StageEffect
}

/** 特效的语义类别——降级开关按类别生效（[FxRenderPolicy]）。 */
enum class FxCategory {
    /** 事件响应类：对用户/服务端动作的直接反馈（摸头爱心、挥手光弧）。particles 关仍播。 */
    EVENT_RESPONSE,

    /** 粒子/氛围类：无事件也持续存在的装饰（接通花瓣）。particles 关不播。 */
    AMBIENCE,
}

/**
 * 资产词表（白名单）与规格元数据。
 *
 * 词表只有这里登记的 id 能进渲染；新增特效 = 放 JSON + 这里加一行 + mapper 加映射。
 * 资产全部仓库自制（tools/gen_lottie_fx.py），许可与规格证据见 assets/fx/README.md。
 */
object FxAssets {
    const val PAT_HEAD = "fx_pat_head"
    const val WAVE = "fx_wave"
    const val CONNECTED = "fx_connected"

    data class Spec(val assetId: String, val category: FxCategory)

    private val registered = listOf(
        Spec(PAT_HEAD, FxCategory.EVENT_RESPONSE),
        // 光弧是对「挥手」这个动作的反馈，不是持续氛围——归事件响应类。
        Spec(WAVE, FxCategory.EVENT_RESPONSE),
        Spec(CONNECTED, FxCategory.AMBIENCE),
    )

    val all: List<Spec> get() = registered

    /** 白名单查询；未登记返回 null（fail-silent，调用方丢弃并记日志）。 */
    fun lookup(assetId: String): Spec? = registered.firstOrNull { it.assetId == assetId }

    fun assetPath(assetId: String): String = "fx/$assetId.json"
}

/**
 * 锚点 → 视口归一化基准点与特效尺寸比例（纯数据，JVM 可测）。
 *
 * 特效层**不跟**舞台捏合变换（方案 §2.3：复刻 View 变换复杂度不值，v1 拍板固定
 * 视口坐标）。放大态下锚点基准点不再对应人物部位，由 [FxRenderPolicy] 决定不播。
 * 基准点是经验值：人物竖屏居中构图，头部约在 y 0.28、手约在 y 0.55——与
 * AvatarStageTap 的 0.42 头/身分界同源（那是判定用，这里是画用，允许不同）。
 */
object FxAnchors {
    data class Norm(val x: Float, val y: Float)

    /** 特效画布宽 = 视口宽 × 该比例。锚点类只盖局部，全屏类铺满。 */
    fun widthFraction(anchor: StageEffect.Anchor): Float = when (anchor) {
        StageEffect.Anchor.HEAD -> 0.62f
        StageEffect.Anchor.BODY -> 0.72f
        StageEffect.Anchor.HAND -> 0.56f
        StageEffect.Anchor.FULL -> 1f
    }

    /** 锚点基准点（视口归一化）。FULL 忽略内容，居中铺满。 */
    fun point(anchor: StageEffect.Anchor): Norm = when (anchor) {
        StageEffect.Anchor.HEAD -> Norm(0.5f, 0.28f)
        StageEffect.Anchor.BODY -> Norm(0.5f, 0.52f)
        StageEffect.Anchor.HAND -> Norm(0.5f, 0.55f)
        StageEffect.Anchor.FULL -> Norm(0.5f, 0.5f)
    }
}
