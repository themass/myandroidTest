package com.openapi.ks.myapp.dh

import com.openapi.ks.myapp.avatar.AvatarRegion

/**
 * Live2D 触点互动的状态机（demo #2）：(服装, 脸红) 两个维度，全纯函数。
 *
 * 为什么不直接发 Emotion 指令走服务端：demo 要的是**本地即时反馈**——点了必有反应，
 * 不赌服务端回不回。反应全在客户端状态里闭环，表情名喂给 native 的
 * `nativeApplyExpression`（资产侧 lab 清单注册，见 scripts/dh-lab/live2d-lab-assets/）。
 *
 * 一个关键约束：vendored Cubism 的表情管理器同时只激活**一个**表情——换装表情
 * 必须把「脸红」一并编进组合参数，否则换装那一瞬脸红会被淡出抹掉。所以表情名
 * 空间是 服装×脸红 的笛卡尔积，Kotlin 与 exp3 资产的一致性由测试钉死。
 */
enum class Live2dCostume { NONE, BIKINI, PAJAMAS }

data class Live2dTouchReactionState(
    val costume: Live2dCostume = Live2dCostume.NONE,
    val blushed: Boolean = false,
)

object Live2dTouchReaction {

    /** 点头切换脸红（摸头反馈）；点身体按 [Live2dCostume] 声明序循环换装。其余区域不动。 */
    fun step(state: Live2dTouchReactionState, region: String): Live2dTouchReactionState = when (region) {
        AvatarRegion.HEAD -> state.copy(blushed = !state.blushed)
        AvatarRegion.BODY -> {
            val values = Live2dCostume.entries
            val next = values[(values.indexOf(state.costume) + 1) % values.size]
            state.copy(costume = next)
        }
        else -> state
    }

    /** 状态 → lab 清单里的表情名；与 exp3 文件一一对应。 */
    fun expressionFor(state: Live2dTouchReactionState): String = when {
        state.costume == Live2dCostume.NONE && !state.blushed -> "base"
        state.costume == Live2dCostume.NONE -> "blush"
        state.costume == Live2dCostume.BIKINI && state.blushed -> "bikini_blush"
        state.costume == Live2dCostume.BIKINI -> "bikini"
        state.blushed -> "pajamas_blush"
        else -> "pajamas"
    }
}
