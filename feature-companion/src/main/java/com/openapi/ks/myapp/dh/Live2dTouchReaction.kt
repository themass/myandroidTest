package com.openapi.ks.myapp.dh

import com.openapi.ks.myapp.avatar.AvatarRegion

/**
 * Live2D 触点互动的状态机（demo #2 → #2b → **v2**），全纯函数。
 *
 * v2 因真机投诉 2026-11-23「点击头和脚只能点一次，再点就没反应了，是设计的问题」
 * 重设计：旧语义是持久 toggle（点一次挂上、再点摘掉），第二次点把状态**还原**——
 * 还原在机器眼里也是变化，在用户眼里是「没反应」。新语义是每个区域**循环**自己
 * 那根轴：头→表情、躯干→上衣、手→手持、脚→鞋。每一次点击都迁移到一个新的
 * 可见状态，「再点没反应」在结构上不可能；显式换装走长按托盘（wardrobeRows）。
 *
 * 为什么不直接发 Emotion 指令走服务端：要的是**本地即时反馈**——点了必有反应，
 * 不赌服务端回不回。反应全在客户端状态里闭环，表情名喂给 native 的
 * `nativeApplyExpression`（资产侧 lab 清单注册，见 scripts/dh-lab/live2d-lab-assets/）。
 *
 * 一个关键约束：vendored Cubism 的表情管理器同时只激活**一个**表情——任何一条
 * 参数改动都必须把其余已激活的维度一并编进组合表情，否则切换那一瞬会被淡出抹掉。
 * 所以表情名空间是四轴的笛卡尔积（5×3×4×3=180），有界的前提是每根轴都是有穷
 * 枚举的循环位。Kotlin 与 exp3 资产的一致性由 Live2dTouchReactionTest 钉死；
 * exp3 里的 (Id,Value) 只允许上游同款模型已验证过的组合（同一测试的
 * upstream-pair 钉桩拦发明值——头饰类参数上游没有已验证值，故 v1 无头饰轴）。
 *
 * 分区语义（v2）：头→表情（正常→脸红→白眼），躯干→上衣（日常→比基尼→睡衣→
 * 南瓜裤→血衣），手→手持（无→kani→小鸟→枕头；原版 ATRI 立绘就抱着 kani，
 * 语义天然），脚→鞋（赤脚→凉鞋→皮鞋）。
 */

/** 上衣（cdi3 标签：衣服-比基尼 / 衣服-睡衣 / 衣服-睡衣-南瓜裤 / 血衣）。 */
enum class Live2dTop { NONE, BIKINI, PAJAMAS, PUMPKIN, BLOOD }

/** 鞋（cdi3 标签：衣服-凉鞋 / 衣服-皮鞋）。 */
enum class Live2dShoes { NONE, SANDAL, LEATHER }

/** 手持（cdi3 标签：kani / 鸟 / 枕头）。 */
enum class Live2dHeld { NONE, KANI, BIRD, PILLOW }

/** 表情（cdi3 标签：脸颊泛红 / 白眼）。 */
enum class Live2dFace { NONE, BLUSH, WHITE }

data class Live2dTouchReactionState(
    val top: Live2dTop = Live2dTop.NONE,
    val shoes: Live2dShoes = Live2dShoes.NONE,
    val held: Live2dHeld = Live2dHeld.NONE,
    val face: Live2dFace = Live2dFace.NONE,
)

/** 托盘里可推进的一根装扮轴。id 与 wardrobe-spec.json 的 slot 一一对应（测试钉死），
 *  active=false 的轴（内衣/头饰）只声明不出货：素材门槛见 spec 与
 *  docs/design/2026-11-23-换装抽象与特效层设计.md。 */
enum class Live2dWardrobeAxis(val id: String, val active: Boolean) {
    TOP("top", true),
    UNDERWEAR("underwear", false),
    SHOES("shoes", true),
    HAIR("hair", false),
    HELD("held", true),
    FACE("face", true),
}

/**
 * 托盘一行：轴名 + 当前值（点一下推进到下一个值）。
 * pending 轴（[enabled]=false）灰显且必须带 [disabledReason]——v2 修掉的是
 * 「假轴」（可用却无变化），灰显+原因是**声明出来**的不可用，两回事。
 */
data class Live2dWardrobeRow(
    val axis: Live2dWardrobeAxis,
    val label: String,
    val value: String,
    val enabled: Boolean = true,
    val disabledReason: String = "",
)

object Live2dTouchReaction {

    private fun <T : Enum<T>> next(v: T, values: Array<T>): T = values[(v.ordinal + 1) % values.size]

    /** 按区域循环对应轴：头→表情、躯干→上衣、手→手持、脚→鞋。其余区域不动。 */
    fun step(state: Live2dTouchReactionState, region: String): Live2dTouchReactionState = when (region) {
        AvatarRegion.HEAD -> state.copy(face = next(state.face, Live2dFace.entries.toTypedArray()))
        AvatarRegion.BODY -> state.copy(top = next(state.top, Live2dTop.entries.toTypedArray()))
        AvatarRegion.HAND -> state.copy(held = next(state.held, Live2dHeld.entries.toTypedArray()))
        AvatarRegion.FOOT -> state.copy(shoes = next(state.shoes, Live2dShoes.entries.toTypedArray()))
        else -> state
    }

    /** 托盘显式推进某一根轴（与分区循环同一状态机、同一状态空间）。
     *  pending 轴（active=false）结构性 no-op：无资产可切。 */
    fun advanceWardrobe(state: Live2dTouchReactionState, axis: Live2dWardrobeAxis): Live2dTouchReactionState =
        if (!axis.active) state
        else when (axis) {
            Live2dWardrobeAxis.TOP -> state.copy(top = next(state.top, Live2dTop.entries.toTypedArray()))
            Live2dWardrobeAxis.SHOES -> state.copy(shoes = next(state.shoes, Live2dShoes.entries.toTypedArray()))
            Live2dWardrobeAxis.HELD -> state.copy(held = next(state.held, Live2dHeld.entries.toTypedArray()))
            Live2dWardrobeAxis.FACE -> state.copy(face = next(state.face, Live2dFace.entries.toTypedArray()))
            Live2dWardrobeAxis.UNDERWEAR, Live2dWardrobeAxis.HAIR -> state // unreachable，保险
        }

    /** 托盘行 = wardrobe-spec.json 的全部槽位（active 可点、pending 灰显带原因）。 */
    fun wardrobeRows(state: Live2dTouchReactionState): List<Live2dWardrobeRow> = listOf(
        Live2dWardrobeRow(Live2dWardrobeAxis.TOP, "上衣", topLabel(state.top)),
        Live2dWardrobeRow(Live2dWardrobeAxis.UNDERWEAR, "内衣", "待素材",
            enabled = false, disabledReason = "需要 Cubism 重制（见 wardrobe-spec.json）"),
        Live2dWardrobeRow(Live2dWardrobeAxis.SHOES, "鞋", shoesLabel(state.shoes)),
        Live2dWardrobeRow(Live2dWardrobeAxis.HAIR, "头饰", "待验证",
            enabled = false, disabledReason = "参数无已验证值"),
        Live2dWardrobeRow(Live2dWardrobeAxis.HELD, "手持", heldLabel(state.held)),
        Live2dWardrobeRow(Live2dWardrobeAxis.FACE, "表情", faceLabel(state.face)),
    )

    private fun topLabel(v: Live2dTop) = when (v) {
        Live2dTop.NONE -> "日常"; Live2dTop.BIKINI -> "比基尼"; Live2dTop.PAJAMAS -> "睡衣"
        Live2dTop.PUMPKIN -> "南瓜裤"; Live2dTop.BLOOD -> "血衣"
    }

    private fun shoesLabel(v: Live2dShoes) = when (v) {
        Live2dShoes.NONE -> "赤脚"; Live2dShoes.SANDAL -> "凉鞋"; Live2dShoes.LEATHER -> "皮鞋"
    }

    private fun heldLabel(v: Live2dHeld) = when (v) {
        Live2dHeld.NONE -> "空手"; Live2dHeld.KANI -> "kani"; Live2dHeld.BIRD -> "小鸟"; Live2dHeld.PILLOW -> "枕头"
    }

    private fun faceLabel(v: Live2dFace) = when (v) {
        Live2dFace.NONE -> "正常"; Live2dFace.BLUSH -> "脸红"; Live2dFace.WHITE -> "白眼"
    }

    // ---- 声明驱动的轴操作（触摸刀 C6：反应从数据来，不写死在 enum 里） ----
    // 状态机的四根轴与 spec 的四个 active 槽是同一批东西；这里只补「按槽位/变体推进」
    // 的入口，让声明里的 cue 能落到轴上。
    //
    // [expressionRegistered] 是**资产事实**闸门：这个模型的 model3 注册了哪些表情名
    // （ATRI 180 个，Amadeus/Yuuka 只有 base+blush——盘点 2026-11-23）。落不进去的 cue
    // 一律不生效：宁可这发触摸没变化（调用方回 Ignored），也不发明一个没有资产的表情名——
    // 与 pending 槽不进命名空间是同一条纪律（Live2dTouchReactionTest 钉死两侧一致）。
    // 这一闸也**取代了旧引擎里按目录名写死的 `fullWardrobe` 分支**：最小模型只剩脸轴能动，
    // 是资产表推出来的结果，不是代码里的 if。

    /** 可被 cue 驱动的槽位名（= wardrobe-spec 里进命名空间的 active 槽）。 */
    fun knownSlots(): Set<String> = setOf("top", "shoes", "held", "face")

    /** 把一条 cue 应用到状态上；未知槽/未知变体/表情未注册 → 原状态。 */
    fun applyCue(
        state: Live2dTouchReactionState,
        cue: TouchCue,
        expressionRegistered: (String) -> Boolean = { true },
    ): Live2dTouchReactionState {
        if (cue.kind == TouchCueKind.MOTION) return state // 表情状态机不管动作
        return when (cue.slot) {
            "top" -> if (cue.kind == TouchCueKind.CYCLE) {
                stepped(state, Live2dTop.entries.toTypedArray(), state.top,
                    { s, v -> s.copy(top = v) }, expressionRegistered)
            } else {
                setAxis(topOf(cue.token), state, { s, v -> s.copy(top = v as Live2dTop) }, expressionRegistered)
            }
            "shoes" -> if (cue.kind == TouchCueKind.CYCLE) {
                stepped(state, Live2dShoes.entries.toTypedArray(), state.shoes,
                    { s, v -> s.copy(shoes = v) }, expressionRegistered)
            } else {
                setAxis(shoesOf(cue.token), state, { s, v -> s.copy(shoes = v as Live2dShoes) }, expressionRegistered)
            }
            "held" -> if (cue.kind == TouchCueKind.CYCLE) {
                stepped(state, Live2dHeld.entries.toTypedArray(), state.held,
                    { s, v -> s.copy(held = v) }, expressionRegistered)
            } else {
                setAxis(heldOf(cue.token), state, { s, v -> s.copy(held = v as Live2dHeld) }, expressionRegistered)
            }
            "face" -> if (cue.kind == TouchCueKind.CYCLE) {
                stepped(state, Live2dFace.entries.toTypedArray(), state.face,
                    { s, v -> s.copy(face = v) }, expressionRegistered)
            } else {
                setAxis(faceOf(cue.token), state, { s, v -> s.copy(face = v as Live2dFace) }, expressionRegistered)
            }
            else -> state
        }
    }

    /** 变体：置成声明值，但该组合表情必须在这个模型注册过。 */
    private fun <T> setAxis(
        target: T?,
        state: Live2dTouchReactionState,
        set: (Live2dTouchReactionState, T) -> Live2dTouchReactionState,
        registered: (String) -> Boolean,
    ): Live2dTouchReactionState {
        val value = target ?: return state
        val candidate = set(state, value)
        return if (registered(expressionFor(candidate))) candidate else state
    }

    /**
     * 循环：在本轴上往后走，**跳过没有资产的那一格**。
     * Amadeus/Yuuka 只有 base+blush：脸轴从 BLUSH 下一格 WHITE 未注册，就继续走到 NONE（=base），
     * 于是「点头 正常↔脸红」是资产表推出来的同一手感，不需要专门的 stepFaceOnly。
     */
    private fun <T : Enum<T>> stepped(
        state: Live2dTouchReactionState,
        values: Array<T>,
        from: T,
        set: (Live2dTouchReactionState, T) -> Live2dTouchReactionState,
        registered: (String) -> Boolean,
    ): Live2dTouchReactionState {
        var v = from
        repeat(values.size) {
            v = next(v, values)
            val candidate = set(state, v)
            if (registered(expressionFor(candidate))) return candidate
        }
        return state
    }

    /** token（wardrobe-spec 的词缀，含 "none"）→ 轴值；不认识返回 null。 */
    private fun topOf(token: String): Live2dTop? = when (token) {
        "none" -> Live2dTop.NONE; "bikini" -> Live2dTop.BIKINI; "pajamas" -> Live2dTop.PAJAMAS
        "pumpkin" -> Live2dTop.PUMPKIN; "blood" -> Live2dTop.BLOOD; else -> null
    }

    private fun shoesOf(token: String): Live2dShoes? = when (token) {
        "none" -> Live2dShoes.NONE; "sandal" -> Live2dShoes.SANDAL; "leather" -> Live2dShoes.LEATHER; else -> null
    }

    private fun heldOf(token: String): Live2dHeld? = when (token) {
        "none" -> Live2dHeld.NONE; "kani" -> Live2dHeld.KANI; "bird" -> Live2dHeld.BIRD
        "pillow" -> Live2dHeld.PILLOW; else -> null
    }

    private fun faceOf(token: String): Live2dFace? = when (token) {
        "none" -> Live2dFace.NONE; "blush" -> Live2dFace.BLUSH; "white" -> Live2dFace.WHITE; else -> null
    }

    /**
     * 状态 → lab 清单里的表情名；与 exp3 文件一一对应。
     * 词缀顺序固定：上衣 → 鞋 → 手持 → 表情（生成器与测试同序）。
     * 每根轴的 token 全局互不相同（sandal/leather、kani/bird/pillow、blush/white…），
     * 所以 180 个名字两两不同——测试钉死。
     */
    fun expressionFor(state: Live2dTouchReactionState): String {
        val parts = listOfNotNull(
            when (state.top) {
                Live2dTop.NONE -> null
                Live2dTop.BIKINI -> "bikini"
                Live2dTop.PAJAMAS -> "pajamas"
                Live2dTop.PUMPKIN -> "pumpkin"
                Live2dTop.BLOOD -> "blood"
            },
            when (state.shoes) {
                Live2dShoes.NONE -> null
                Live2dShoes.SANDAL -> "sandal"
                Live2dShoes.LEATHER -> "leather"
            },
            when (state.held) {
                Live2dHeld.NONE -> null
                Live2dHeld.KANI -> "kani"
                Live2dHeld.BIRD -> "bird"
                Live2dHeld.PILLOW -> "pillow"
            },
            when (state.face) {
                Live2dFace.NONE -> null
                Live2dFace.BLUSH -> "blush"
                Live2dFace.WHITE -> "white"
            },
        )
        return if (parts.isEmpty()) "base" else parts.joinToString("_")
    }

    // ---- 最小装扮层去哪了（触摸刀 C6）----
    // 旧代码在这里写死「Amadeus/Yuuka 只开放脸轴」（stepFaceOnly / expressionForFaceOnly，
    // 引擎侧还有 `fullWardrobe = 目录名 == "ATRI"`）。那是**资产盘点事实**（Yuuka 零 exp3、
    // Amadeus 只有 logo 开关，跨模型唯一已验证值是 ParamCheek）被硬编码成了分支。
    // 现在闸在数据侧：[applyCue] 带 `expressionRegistered`，循环会跳过这个模型没注册的表情格，
    // 于是 base+blush 的模型自然得到「点头 正常↔脸红」，其余轴自然落不到可用反应
    // （引擎回 Ignored，不做假轴）。同一手感，来源从代码换成了包。

    /**
     * 这根轴在当前模型**有没有可切的表情**（收纳盒/托盘的灰显判据）。
     * 判据就是「推进一根轴能不能真的变」——与 [applyCue] 用同一个闸，两处不会打架。
     */
    fun axisHasAssets(
        axis: Live2dWardrobeAxis,
        state: Live2dTouchReactionState,
        expressionRegistered: (String) -> Boolean,
    ): Boolean {
        if (!axis.active) return false
        return applyCue(state, TouchCue(TouchCueKind.CYCLE, slot = axis.id), expressionRegistered) != state
    }
}
