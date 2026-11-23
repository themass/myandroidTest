package com.openapi.feature.companion.fx

/**
 * 「这条特效此刻到底怎么画」的唯一决策点（纯函数，验收判据钉在这里）。
 *
 * 两条降级都是方案 §2.4 的验收判据，写成可测函数而不是散在 composable 里：
 * - reducedMotion（系统动画缩放=0）→ 降级为静态帧（progress scrubbing 停在代表帧）
 *   或完全不播；粒子/氛围类在减弱动态下直接不播——静态花瓣等于屏幕贴纸片。
 * - particles 关（通话页飘散粒子同开关）→ 粒子/氛围类不播，事件响应类仍播。
 *
 * 放大态退化（方案 §2.3）：特效层不跟舞台捏合变换，放大后锚点基准点对不上人物，
 * 锚点类特效退化为「不播」（全屏类不受影响——它本来就是视口构图）。
 */
object FxRenderPolicy {

    /** 渲染形态：动画播一遍/循环，或停在代表帧的静态图。 */
    enum class Mode { PLAY, STATIC_FRAME, SKIP }

    data class Decision(val mode: Mode, val reason: SkipReason? = null)

    enum class SkipReason { REDUCED_MOTION_AMBIENCE, PARTICLES_OFF, ZOOMED_ANCHOR }

    /**
     * @param reducedMotion 系统「移除动画」（ANIMATOR_DURATION_SCALE=0），与舞台脉冲同源。
     * @param particles     通话页飘散粒子开关（options.particles）。
     * @param stageZoomed   舞台捏合是否处于放大态（AvatarStageTransformState.isZoomed）。
     *                      传入的是布尔快照——特效层读判定器状态，不认识引擎。
     */
    fun decide(
        effect: StageEffect.Play,
        reducedMotion: Boolean,
        particles: Boolean,
        stageZoomed: Boolean,
    ): Decision {
        val spec = FxAssets.lookup(effect.assetId) ?: return Decision(Mode.SKIP, null)
        val ambience = spec.category == FxCategory.AMBIENCE

        // 减弱动态：氛围类不播；事件响应类降静态帧（保留「发生了什么」的反馈）。
        if (reducedMotion && ambience) return Decision(Mode.SKIP, SkipReason.REDUCED_MOTION_AMBIENCE)

        // particles 关：只禁粒子/氛围类；事件响应类是动作反馈，不是装饰。
        if (!particles && ambience) return Decision(Mode.SKIP, SkipReason.PARTICLES_OFF)

        // 放大态：锚点类基准点失效。全屏氛围类不受影响；事件响应类也退不播——
        // 放大看的是人物本身，弹个对不上位置的爱心比不弹更糟（v1 拍板）。
        if (stageZoomed && effect.anchor != StageEffect.Anchor.FULL) {
            return Decision(Mode.SKIP, SkipReason.ZOOMED_ANCHOR)
        }

        return if (reducedMotion) Decision(Mode.STATIC_FRAME) else Decision(Mode.PLAY)
    }
}
