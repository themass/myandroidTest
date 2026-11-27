package com.openapi.feature.companion.fx

import com.openapi.ks.myapp.companion.TransportConnection

/**
 * 触发源 → [StageEffect] 的唯一翻译点（纯函数，JVM 单测钉死）。
 *
 * 纪律照 [com.openapi.ks.myapp.avatar.AvatarEventMapper]：未知输入一律 null，
 * 绝不抛异常。特效是「锦上添花」，一个没见过的动作 id 绝不该把通话流程带崩。
 *
 * **不另起事件总线**（架构纪律 2026-11-23）：运行时所有触发都并进既有
 * `cmp.cue → AvatarJson → AvatarEventMapper → AvatarCommand.Gesture(id)` 词表，
 * 落到 [CompanionUiState.gesture]/[CompanionUiState.gestureSeq]；特效层只订阅
 * 这条既有 StateFlow 镜像，再用本 mapper 翻成 [StageEffect]。本对象只做词表翻译。
 *
 * 三条入口：
 * - [fromGesture]：服务端 cue 的 gesture id，以及本地并进同一词表的 id（托盘/接通/面板直发）；
 * - [fromAction]：托盘动作 id → 特效（与 gesture 词表对齐；测试/直接映射用）；
 * - [onCallState]：传输层连接迁移是否该播接通特效（返回特效；运行时据此写入 gesture=`connected`）。
 *
 * **触发词的唯一来源是 [gestureAliases]**（用户 2026-11-28 扩词表后新增的纪律）：
 * 面板要展示的 gesture id、`fromGesture` 认的词、去重与测试都在同一张表上，
 * 因此「加了资产忘了加词」和「两个特效抢同一个词」都由 [FxWordTableTest] 钉住。
 * 服务端没发过的词不会自己出现——面板/教学脚本走 `emitStageGesture(primary)` 直发，
 * 走的仍是同一条 gesture 流（见 `CompanionRoute.onLabFxTest`）。
 */
object StageEffectMapper {

    /** 接通特效在 Gesture 词表里的 id——本地写入 uiState.gesture，与服务端 cue 同路。 */
    const val GESTURE_CONNECTED = "connected"

    /**
     * 资产 → 触发词（第一个是**主词**，面板与直发路径用它；其余是别名）。
     *
     * 主词一律等于资产 id 去掉 `fx_` 前缀：面板、日志、服务端将来下发同一个词，
     * 三处不用背两套命名。别名只收「同一动作的自然说法」（`waves`/`greeting`），
     * 不收语义不同的词——那属于新特效，该走新资产。
     */
    private val gestureAliases: Map<String, List<String>> = linkedMapOf(
        FxAssets.PAT_HEAD to listOf("pat_head", "pathead", "head_pat"),
        FxAssets.WAVE to listOf("wave", "waves", "greeting"),
        FxAssets.CONNECTED to listOf(GESTURE_CONNECTED, "connect", "call_connected"),
        // 用户手指滑动（本地上报，见 camera-fx CameraSwipeCue）→ 中性流光横扫。
        // 词表载荷无方向字段（协议不扩），素材左右对称、不指方向。
        FxAssets.SWIPE to listOf("swipe"),

        // 头部：都是「对她做某件事」的即时反馈词。
        FxAssets.CAT_EARS to listOf("cat_ears", "cat", "ears"),
        FxAssets.STAR_EYES to listOf("star_eyes", "stars", "star_struck"),
        FxAssets.BLUSH to listOf("blush", "shy", "blushing"),

        // 身体。
        FxAssets.BUBBLES to listOf("bubbles", "bubble"),
        FxAssets.HALO to listOf("halo", "angel"),

        // 手部（与相机侧「张手/捏合」cue 同一条流，词不同不冲突）。
        FxAssets.SPARKLE to listOf("sparkle", "sparkles"),

        // 全屏事件：庆祝类。
        FxAssets.FIREWORKS to listOf("fireworks", "firework", "celebrate"),
        FxAssets.COINS to listOf("coins", "coin"),

        // 全屏氛围：进入某个「场景」的开关词，由面板/服务端按需常开。
        FxAssets.HEART_RAIN to listOf("heart_rain", "hearts"),
        FxAssets.SNOW to listOf("snow", "snowfall"),
        FxAssets.RAIN to listOf("rain", "rainfall"),
        FxAssets.SPOTLIGHT to listOf("spotlight", "spot"),
    )

    private val gestureToAsset: Map<String, String> by lazy {
        gestureAliases.entries.flatMap { (assetId, aliases) ->
            aliases.map { it.lowercase() to assetId }
        }.toMap()
    }

    /** 面板/直发路径用的主触发词；未登记资产返回 null。 */
    fun primaryGesture(assetId: String): String? = gestureAliases[assetId]?.firstOrNull()

    /** 某资产的全部触发词（含别名）；顺序稳定，主词在首位。 */
    fun gesturesFor(assetId: String): List<String> = gestureAliases[assetId].orEmpty()

    /**
     * Gesture id → 特效。词表白名单：认得的翻成特效，认不得的（含空串）回 null。
     * 服务端与本地托盘/接通/面板共用这一套，避免两套映射。锚点取自 [FxAssets.Spec]，
     * 不在分支里各写一份——词表改了，画位跟着改。
     */
    fun fromGesture(gestureId: String?): StageEffect? =
        gestureId?.trim()?.lowercase()?.let { id -> gestureToAsset[id] }?.let { play(it) }

    /** 本地托盘动作 id → 特效。置灰动作（turn/walk/nod）没有特效。 */
    fun fromAction(actionId: String?): StageEffect? =
        actionId?.trim()?.lowercase()?.let { id ->
            when (id) {
                "pat_head" -> play(FxAssets.PAT_HEAD)
                "wave" -> play(FxAssets.WAVE)
                // sit_close 是距离语义，一期不给特效。
                else -> null
            }
        }

    /**
     * 连接状态迁移 → 特效。只有「首次真正接通」：prev 为 null 或 CONNECTING。
     * 重连恢复（RECONNECTING/DISCONNECTED → CONNECTED）不播——LiveKit 常态。
     * 运行时：非 null 则 [com.openapi.ks.myapp.companion.CompanionViewModel.emitStageGesture]
     * (`connected`)，并进既有 gestureSeq 流，不另起总线。
     */
    fun onCallState(prev: TransportConnection?, next: TransportConnection): StageEffect? {
        return when {
            next != TransportConnection.CONNECTED -> null
            prev == null || prev == TransportConnection.CONNECTING ->
                play(FxAssets.CONNECTED, repeat = 1)
            else -> null
        }
    }

    /** 白名单外的 id 回 null（fail-silent）；锚点一律取词表登记值。 */
    private fun play(assetId: String, repeat: Int = 1): StageEffect? =
        FxAssets.lookup(assetId)?.let { spec -> StageEffect.Play(spec.assetId, spec.anchor, repeat) }
}
