package com.openapi.ks.myapp.dh

import com.openapi.ks.myapp.avatar.AvatarRegion

/**
 * Live2D 触点互动的状态机（demo #2 起步，#2b 扩到手/脚）：
 * (服装, 脸红, 手里kani, 凉鞋) 四个维度，全纯函数。
 *
 * 为什么不直接发 Emotion 指令走服务端：demo 要的是**本地即时反馈**——点了必有反应，
 * 不赌服务端回不回。反应全在客户端状态里闭环，表情名喂给 native 的
 * `nativeApplyExpression`（资产侧 lab 清单注册，见 scripts/dh-lab/live2d-lab-assets/）。
 *
 * 一个关键约束：vendored Cubism 的表情管理器同时只激活**一个**表情——任何一条
 * 参数改动都必须把其余已激活的维度一并编进组合表情，否则切换那一瞬会被淡出抹掉。
 * 所以表情名空间是四个维度的笛卡尔积（3×2×2×2=24），有界的前提是四个维度都是
 * 持久 toggle（点一次挂上、再点摘掉）而不是叠计数。Kotlin 与 exp3 资产的一致性
 * 由 Live2dTouchReactionTest 钉死；exp3 里的 (Id,Value) 只允许上游同款模型
 * 已验证过的组合（同一测试的 upstream-pair 钉桩拦发明值）。
 *
 * 分区反应（#2b）：头→脸红（保留 #2 语义），躯干→循环换装（保留 #2 语义），
 * 手→手里出现 kani（原版 ATRI 立绘就抱着它，语义天然），脚→凉鞋上脚。
 */
enum class Live2dCostume { NONE, BIKINI, PAJAMAS }

data class Live2dTouchReactionState(
    val costume: Live2dCostume = Live2dCostume.NONE,
    val blushed: Boolean = false,
    /** 手（demo #2b）：Param38「kani」。 */
    val crab: Boolean = false,
    /** 脚（demo #2b）：Param19「衣服-凉鞋」。 */
    val sandal: Boolean = false,
)

object Live2dTouchReaction {

    /** 按区域迁移：头切脸红、躯干循环换装、手切 kani、脚切凉鞋。其余区域不动。 */
    fun step(state: Live2dTouchReactionState, region: String): Live2dTouchReactionState = when (region) {
        AvatarRegion.HEAD -> state.copy(blushed = !state.blushed)
        AvatarRegion.BODY -> {
            val values = Live2dCostume.entries
            val next = values[(values.indexOf(state.costume) + 1) % values.size]
            state.copy(costume = next)
        }
        AvatarRegion.HAND -> state.copy(crab = !state.crab)
        AvatarRegion.FOOT -> state.copy(sandal = !state.sandal)
        else -> state
    }

    /**
     * 状态 → lab 清单里的表情名；与 exp3 文件一一对应。
     * 词缀顺序固定：服装 → 脸红 → kani → 凉鞋（生成器与测试同序，#2 的 6 个老名字不变）。
     */
    fun expressionFor(state: Live2dTouchReactionState): String {
        val parts = listOfNotNull(
            when (state.costume) {
                Live2dCostume.NONE -> null
                Live2dCostume.BIKINI -> "bikini"
                Live2dCostume.PAJAMAS -> "pajamas"
            },
            "blush".takeIf { state.blushed },
            "crab".takeIf { state.crab },
            "sandal".takeIf { state.sandal },
        )
        return if (parts.isEmpty()) "base" else parts.joinToString("_")
    }
}
