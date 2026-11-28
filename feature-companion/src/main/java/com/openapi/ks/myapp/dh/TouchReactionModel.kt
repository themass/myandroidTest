package com.openapi.ks.myapp.dh

import com.openapi.ks.myapp.avatar.AvatarRegion

/**
 * 触摸反应的数据模型 + 命中路由 + 反应选择（超越 OLLMV 第四刀 C6，全纯函数）。
 *
 * 为什么要这一层：`Live2dTouchReaction` 的 v2 状态机把「点哪儿 → 动哪根轴」写死在
 * Kotlin 的四个 enum 里，换形象 / 调手感都要改代码重编（`surpass-ollmv-synthesis.md:19`
 * C6 指的就是这个）。这一层把**命中区与反应变成声明**（wardrobe-spec 的
 * `touchReactions`），状态机退成「可被 cue 驱动的轴」，并给出**三级回落**：
 *
 *   DECLARED（spec 声明）→ MODEL_OWN（模型包自带的动作表）→ BUILT_IN（兜底分区 + 循环）
 *
 * 命中语义钉死自 `docs/research/2026-11-28-waifu-desktop-pet-deepread.md` §1 结论 1 与
 * §10.3 收紧 2（全场四家桌宠都踩过的那几个坑）：
 * 1. **区域自带稳定 id**，绝不复用 Cubism `HitAreas[].Name`（空名/重名会静默塌表：
 *    pixi `InternalModel.ts:170-176`；NyaDeskPet 为补丁写了整段 `patchHitAreas`）——
 *    语义 id 同时是防抖与仲裁的 key，所以小集合、可枚举、与上报 agent 的 region 词表一致；
 * 2. **重叠必须有显式优先序**：现存每一家都是「未声明的第一个赢」（pixi 按对象 key 序、
 *    dsh-pet `Array.find` 靠 manifest 顺序），我们按 `priority` 小的赢，声明顺序只作同值兜底；
 * 3. **区域附着在模型上，不附着在动作上**（shimeji 的 `<Hotspot>` 挂在 `<Animation>` 上、
 *    每帧重建 —— `AnimationBuilder.java:271-274`）：所以路由只吃归一化坐标 + 声明，
 *    当前在播什么 motion 与命中无关；
 * 4. **几何要超出单个 rect**：一个区可以挂多条 rect（ATRI 的两臂 = 一个区的两条矩形）。
 */

/**
 * 命中矩形，归一化舞台坐标。区间是 `[min, max)`；贴舞台边缘那一侧（max==1）含端点。
 *
 * 边界按 **Float** 比较（声明里写的是十进制数，进来的坐标是 Float）：旧判据
 * [live2dTapZone] 比的就是 Float，同一批阈值只有同精度才谈得上「换刀不换分区手感」。
 */
data class TouchRect(val x0: Double, val y0: Double, val x1: Double, val y1: Double) {

    fun contains(x: Float, y: Float): Boolean = inAxis(x, x0.toFloat(), x1.toFloat()) && inAxis(y, y0.toFloat(), y1.toFloat())

    // 半开区间让相邻区不双算接缝；但 1.0 是坐标夹取的边界值（出界点当边缘点），
    // 若也按排除处理，铺到右/下边缘的区会在最后一列留出「点了没反应」的缝。
    private fun inAxis(v: Float, min: Float, max: Float): Boolean =
        if (max >= 1f) v in min..max else v >= min && v < max
}

/** 反应 cue 的种类。能力门按这个过滤：运行时不支持的种类不参与抽签。 */
enum class TouchCueKind {
    /** 把某根槽位直接置成声明的变体（→ 组合表情名）。 */
    VARIANT,

    /** 推进某根槽位到下一个值（= 现状 v2 的循环语义）。 */
    CYCLE,

    /** 播一个模型动作。当前 fork 的 JNI 只有 `nativeApplyExpression`（无 motion API），
     *  所以这一项目前只被声明与校验，不会被选中播放——能力门见 [TouchReactionDecision]。 */
    MOTION,
}

/**
 * 一条候选反应。
 *
 * `weight` 是区内相对权重（正整数）：同一区多条候选按权重抽签，让「同一处连点」不机械重复。
 * 变体 cue 只**引用** wardrobe-spec 已声明的 slot/token——不发明资产（表情名空间由
 * `expressionFor` 生成，见 [Live2dTouchReaction] 类注释的笛卡尔积约束）。
 */
data class TouchCue(
    val kind: TouchCueKind,
    val slot: String = "",
    val token: String = "",
    val motionId: String = "",
    val weight: Int = 1,
) {
    /** 区内稳定身份（「上一发播过什么」的 key；防抖/仲裁也走这个）。 */
    fun key(): String = when (kind) {
        TouchCueKind.VARIANT -> "$slot:$token"
        TouchCueKind.CYCLE -> "$slot:cycle"
        TouchCueKind.MOTION -> "motion:$motionId"
    }
}

/** 一个命中区：稳定 id + 显式优先序 + 若干几何 + 若干候选反应。 */
data class TouchReactionZone(
    val id: String,
    val label: String,
    val priority: Int,
    val rects: List<TouchRect>,
    val reactions: List<TouchCue>,
)

/** 某个模型包的完整触摸声明。 */
data class TouchReactionDeclaration(val zones: List<TouchReactionZone>)

/**
 * 命中路由：坐标 → 区。纯函数，无状态、无 IO。
 *
 * [builtIn] 是**没有声明时**的兜底分区，阈值与 [live2dTapZone] 同一套真机实测值
 * （头 y<0.42、脚 y≥0.57、两臂 |x−0.5|≥0.12、其余躯干）。它只声明区、不声明反应，
 * 所以路由完必然落到 [ReactionLevel.BUILT_IN]——换刀不换分区手感。
 */
object TouchZoneRouter {

    val builtIn: TouchReactionDeclaration = TouchReactionDeclaration(
        zones = listOf(
            // 两臂 = 一个区两条矩形（§10 判定 ④：几何要超出单个 rect）。
            TouchReactionZone(
                "hand", "手", 0,
                listOf(TouchRect(0.0, 0.42, 0.38, 0.57), TouchRect(0.62, 0.42, 1.0, 0.57)),
                emptyList(),
            ),
            // 躯干铺满整条带：靠 priority（hand=0 先赢两臂）而不是靠挖洞，
            // 这样「重叠有显式优先序」这条纪律在兜底层也成立。
            TouchReactionZone("body", "躯干", 1, listOf(TouchRect(0.0, 0.42, 1.0, 0.57)), emptyList()),
            TouchReactionZone("head", "头", 2, listOf(TouchRect(0.0, 0.0, 1.0, 0.42)), emptyList()),
            TouchReactionZone("foot", "脚", 3, listOf(TouchRect(0.0, 0.57, 1.0, 1.0)), emptyList()),
        ),
    )

    /** 命中哪个区；坐标先夹到 [0,1]（出界点当边缘点，而不是丢掉这一发触摸）。 */
    fun hit(declaration: TouchReactionDeclaration?, x: Float, y: Float): TouchReactionZone? {
        val zones = declaration?.zones ?: return null
        val cx = x.coerceIn(0f, 1f)
        val cy = y.coerceIn(0f, 1f)
        // 显式优先序赢；同值时按声明顺序（稳定可预期，不是「碰巧第一个」）。
        return zones.withIndex()
            .sortedWith(compareBy({ it.value.priority }, { it.index }))
            .map { it.value }
            .firstOrNull { zone -> zone.rects.any { it.contains(cx, cy) } }
    }
}

/** 反应来自哪一级（回落链的位置，日志与验收要看）。 */
enum class ReactionLevel { DECLARED, MODEL_OWN, BUILT_IN }

/** 一次触摸的完整决定：哪级、哪个区、哪条 cue、应用后的状态、有没有真的变化。 */
data class ReactionChoice(
    val level: ReactionLevel,
    val zoneId: String?,
    val cue: TouchCue?,
    val state: Live2dTouchReactionState,
    val changed: Boolean,
) {
    /** 上报用的区域名：命中区 id；没命中区时退到旧的 head/body 语义由调用方兜。 */
    val region: String? get() = zoneId
}

/**
 * 反应选择 + 回落链（纯决策层；[roll] 注入使抽签可测）。
 *
 * 「无变化保护」是这一层最重要的不变式：真机投诉 2026-11-23「点一次就没反应」的根因是
 * 第二次点把状态**还原**。v2 用循环根治；声明化以后随机抽签可能重新撞上当前值，或者撞上
 * 这个模型**没有注册**的表情（Amadeus/Yuuka 只有 base+blush），所以可行性判定按
 * 「应用后状态真的变了」来做，并在候选池里就把不可行的 cue 滤掉。三级都不可行时
 * `changed=false`，由调用方回 `Ignored`——「点了没动静」的假轴不做（v2 同一条纪律）。
 */
object TouchReactionDecision {

    /** 区域 → 兜底轴（与 v2 硬编码的映射同源）。未知区名退回头轴。 */
    fun builtInSlotFor(zoneId: String?): String = when (zoneId) {
        AvatarRegion.BODY -> "top"
        AvatarRegion.HAND -> "held"
        AvatarRegion.FOOT -> "shoes"
        else -> "face"
    }

    /**
     * 按权重抽签。[roll] 非负即可（内部取模回绕）。
     *
     * [excludeId] 是上一发播过的 cue：连点同一处要换样。但**只剩它时不排除**——
     * 宁可重复也不回「没反应」。[viable] 是资产可行性（表情在这个模型注册过吗、
     * 应用后真的变化吗），不可行的候选根本不进池子。
     */
    fun pickWeighted(
        cues: List<TouchCue>,
        roll: Int,
        excludeId: String? = null,
        viable: (TouchCue) -> Boolean = { true },
    ): TouchCue? {
        val pool = cues.filter { it.weight > 0 && viable(it) }
        if (pool.isEmpty()) return null
        val withoutLast = pool.filterNot { it.key() == excludeId }
        val candidates = withoutLast.ifEmpty { pool }
        val total = candidates.sumOf { it.weight }
        var r = ((roll % total) + total) % total
        for (cue in candidates) {
            if (r < cue.weight) return cue
            r -= cue.weight
        }
        return candidates.last() // 理论不可达（r < total），保险
    }

    /** 只选 cue，不碰状态机（级别判定用；可行性默认放开）。 */
    fun resolve(
        hit: TouchReactionZone?,
        modelOwnCues: List<TouchCue>,
        supported: Set<TouchCueKind>,
        lastCueKey: String?,
        roll: Int,
    ): ReactionChoice {
        val declared = hit?.reactions.orEmpty().filter { it.kind in supported }
        if (declared.isNotEmpty()) {
            val cue = pickWeighted(declared, roll, lastCueKey)
            return ReactionChoice(ReactionLevel.DECLARED, hit?.id, cue, Live2dTouchReactionState(), cue != null)
        }
        val modelOwn = modelOwnCues.filter { it.kind in supported }
        if (modelOwn.isNotEmpty()) {
            val cue = pickWeighted(modelOwn, roll, lastCueKey)
            return ReactionChoice(ReactionLevel.MODEL_OWN, hit?.id, cue, Live2dTouchReactionState(), cue != null)
        }
        val cycle = TouchCue(TouchCueKind.CYCLE, slot = builtInSlotFor(hit?.id))
        return ReactionChoice(ReactionLevel.BUILT_IN, hit?.id, cycle, Live2dTouchReactionState(), false)
    }

    /**
     * 完整决定：走三级回落，每级都按可行性筛候选，第一级给得出可行 cue 的就用它。
     *
     * [expressionGate] 是**资产事实**闸门（这个模型的 model3 注册了哪些表情名）：
     * 落在闸外的声明不参与抽签。它取代了旧引擎里 `fullWardrobe` 那种「按目录名写死」的分支
     * ——最小模型只剩 base/blush 是资产盘点出来的结果，不是代码里的 if。
     */
    fun resolveTap(
        hit: TouchReactionZone?,
        modelOwnCues: List<TouchCue>,
        supported: Set<TouchCueKind>,
        lastCueKey: String?,
        roll: Int,
        state: Live2dTouchReactionState,
        expressionGate: (String) -> Boolean = { true },
    ): ReactionChoice {
        fun viable(cue: TouchCue): Boolean =
            Live2dTouchReaction.applyCue(state, cue, expressionGate) != state

        val declared = hit?.reactions.orEmpty().filter { it.kind in supported }
        pickWeighted(declared, roll, lastCueKey, ::viable)?.let {
            return commit(ReactionLevel.DECLARED, hit?.id, it, state, expressionGate)
        }
        val modelOwn = modelOwnCues.filter { it.kind in supported }
        pickWeighted(modelOwn, roll, lastCueKey, ::viable)?.let {
            return commit(ReactionLevel.MODEL_OWN, hit?.id, it, state, expressionGate)
        }
        val cycle = TouchCue(TouchCueKind.CYCLE, slot = builtInSlotFor(hit?.id))
        return if (viable(cycle)) {
            commit(ReactionLevel.BUILT_IN, hit?.id, cycle, state, expressionGate)
        } else {
            ReactionChoice(ReactionLevel.BUILT_IN, hit?.id, null, state, false)
        }
    }

    private fun commit(
        level: ReactionLevel,
        zoneId: String?,
        cue: TouchCue,
        state: Live2dTouchReactionState,
        expressionGate: (String) -> Boolean,
    ): ReactionChoice {
        val after = Live2dTouchReaction.applyCue(state, cue, expressionGate)
        return ReactionChoice(level, zoneId, cue, after, after != state)
    }
}
