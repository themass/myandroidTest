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
 * 词表只有这里登记的 id 能进渲染；新增特效 = 生成 JSON + 这里加一行 + mapper 加触发词。
 * 资产全部仓库自制（tools/gen_lottie_fx.py），许可与规格证据见 assets/fx/README.md。
 *
 * 规模口径（用户 2026-11-28：「特效只有 4 个……常规的短视频特效不是该有么」）：
 * HER 侧扩到 16 条，覆盖 头部 / 身体 / 手部 / 全屏 四类锚点 × 事件响应 / 氛围 两类语义。
 * [Spec.anchor] 登记在词表里（而不是散在 mapper 的 when 分支），这样「锚点 + 类别」
 * 一眼可审，降级判定与面板置灰都从同一处取。
 */
object FxAssets {
    // ---- 既有四条 ----------------------------------------------------------
    const val PAT_HEAD = "fx_pat_head"
    const val WAVE = "fx_wave"
    const val CONNECTED = "fx_connected"
    const val SWIPE = "fx_swipe"

    // ---- 头部锚点（人物头上）----------------------------------------------
    const val CAT_EARS = "fx_cat_ears"
    const val STAR_EYES = "fx_star_eyes"
    const val BLUSH = "fx_blush"

    // ---- 身体锚点 ----------------------------------------------------------
    const val BUBBLES = "fx_bubbles"
    const val HALO = "fx_halo"

    // ---- 手部锚点 ----------------------------------------------------------
    const val SPARKLE = "fx_sparkle"

    // ---- 全屏：事件响应 ----------------------------------------------------
    const val FIREWORKS = "fx_fireworks"
    const val COINS = "fx_coins"

    // ---- 全屏：氛围 --------------------------------------------------------
    const val HEART_RAIN = "fx_heart_rain"
    const val SNOW = "fx_snow"
    const val RAIN = "fx_rain"
    const val SPOTLIGHT = "fx_spotlight"

    data class Spec(
        val assetId: String,
        val category: FxCategory,
        /** 该特效默认的画位锚点；[StageEffectMapper] 从这里取，不在分支里各写一套。 */
        val anchor: StageEffect.Anchor,
    )

    private val registered = listOf(
        Spec(PAT_HEAD, FxCategory.EVENT_RESPONSE, StageEffect.Anchor.HEAD),
        // 光弧是对「挥手」这个动作的反馈，不是持续氛围——归事件响应类。
        Spec(WAVE, FxCategory.EVENT_RESPONSE, StageEffect.Anchor.HAND),
        Spec(CONNECTED, FxCategory.AMBIENCE, StageEffect.Anchor.FULL),
        // 流光是对「手指滑动」cue（CameraSwipeCue）的反馈——事件响应类。
        Spec(SWIPE, FxCategory.EVENT_RESPONSE, StageEffect.Anchor.FULL),

        // 猫耳/星星眼/脸红都是「对她做某件事」的即时反馈（摸头、夸、调戏），
        // 不是常驻装饰：particles 关也要播，放大态则不画（锚点对不上人）。
        Spec(CAT_EARS, FxCategory.EVENT_RESPONSE, StageEffect.Anchor.HEAD),
        Spec(STAR_EYES, FxCategory.EVENT_RESPONSE, StageEffect.Anchor.HEAD),
        Spec(BLUSH, FxCategory.EVENT_RESPONSE, StageEffect.Anchor.HEAD),

        // 气泡从身上冒出、光环围身体转——都是身体锚点上的一次性反馈。
        Spec(BUBBLES, FxCategory.EVENT_RESPONSE, StageEffect.Anchor.BODY),
        Spec(HALO, FxCategory.EVENT_RESPONSE, StageEffect.Anchor.BODY),

        Spec(SPARKLE, FxCategory.EVENT_RESPONSE, StageEffect.Anchor.HAND),

        // 烟花/金币：一次庆祝事件，铺满视口，particles 关仍播（它是反馈不是装饰）。
        Spec(FIREWORKS, FxCategory.EVENT_RESPONSE, StageEffect.Anchor.FULL),
        Spec(COINS, FxCategory.EVENT_RESPONSE, StageEffect.Anchor.FULL),

        // 爱心雨/雪花/雨滴/追光：没有事件也一直存在的氛围——particles 关必须全停。
        Spec(HEART_RAIN, FxCategory.AMBIENCE, StageEffect.Anchor.FULL),
        Spec(SNOW, FxCategory.AMBIENCE, StageEffect.Anchor.FULL),
        Spec(RAIN, FxCategory.AMBIENCE, StageEffect.Anchor.FULL),
        Spec(SPOTLIGHT, FxCategory.AMBIENCE, StageEffect.Anchor.FULL),
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
