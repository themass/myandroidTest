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

    // ---- Amadeus / Yuuka 的最小装扮层 ----
    // 这两个模型没有上游已验证的换装参数（盘点 2026-11-23：Yuuka 零 exp3、
    // Amadeus 只有 logo 开关），跨模型唯一语义一致的已验证值是 ParamCheek=1
    // （三张 cdi3 都标「脸颊」）。所以它们只开放脸轴（正常↔脸红），每次点头
    // 都有可见变化；其余区域由引擎判 Ignored，不做「点了没动静」的假轴。

    /** 最小模型的头轴：只在 BLUSH/NONE 间循环（WHITE 没有已验证资产）。 */
    fun stepFaceOnly(state: Live2dTouchReactionState, region: String): Live2dTouchReactionState =
        if (region == AvatarRegion.HEAD) {
            state.copy(face = if (state.face == Live2dFace.BLUSH) Live2dFace.NONE else Live2dFace.BLUSH)
        } else {
            state
        }

    /** 最小模型的清单名：只有 base 与 blush 注册在案。 */
    fun expressionForFaceOnly(state: Live2dTouchReactionState): String =
        if (state.face == Live2dFace.BLUSH) "blush" else "base"
}
