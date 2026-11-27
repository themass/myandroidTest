package com.openapi.ks.myapp.companion

import com.openapi.ks.myapp.dh.StageChoice
import com.openapi.ks.myapp.dh.engineIdForRenderer
import com.openapi.ks.myapp.dh.roleKeyFromHerId

/**
 * her 列表页（OpenDesign 稿 02「她长什么样」）的纯决策层。UI 只做两件事：画这里给的
 * 结果、把点击转成一个 [StageChoice] 写回 `DhLabPreferences.callStageChoice`——
 * **选择状态只有那一份**，通话页的 [com.openapi.ks.myapp.dh.resolveCallStage] 仍是唯一
 * 生效点（persona/her 分层架构 §4「进房」her 半）。
 *
 * 三条不能松的口径：
 * 1. 只列**能证明属于这个角色**的 her：id 桥约定 `<renderer>-<role>-vN`（见
 *    [roleKeyFromHerId]）。端上不维护角色↔her 对照表，证明不了就不列——把别人的形象
 *    列给用户点，比列表空着更糟；
 * 2. 卡片可不可点 = 点了能不能兑现：只有发布态、且 renderer 在这端映射得出舞台，才写选择
 *    （草稿与未接入的 renderer 显示出来但不可选，不假装能换）；
 * 3. 高亮没有独立真源：`StageChoice` 里只存「原形象 / 某引擎」，不存 her id，所以
 *    Unset 时高亮落在**服务端默认那一张**（另标「默认跟随」），显式选择时落在第一张
 *    能产生该舞台的卡上。列表页绝不偷偷把 Unset 写成显式值。
 */

/** 这张卡能不能被用户选。UI 据此决定点击回调与卡片上的说明文字。 */
enum class HerSelectability {
    /** 点了就换：写一条 [StageChoice]，下一次进通话页生效。 */
    SELECTABLE,

    /** 草稿（或未上架）：能看到，但点了不生效——只有发布态驱动舞台。 */
    NOT_PUBLISHED,

    /** 服务端已注册、这端还接不了的 renderer（如 talkinghead 还没上引擎映射）。 */
    RENDERER_UNSUPPORTED,
}

/** 列表页当前的选中态：哪张卡描边、是不是「跟随服务端默认」。 */
data class HerListSelection(
    /** null = 没有任何一张卡代表当前的舞台（这角色没 her，或用户选过它没有的引擎）。 */
    val selectedHerId: String?,
    /** true：这一张是服务端给的默认（用户从没表达过偏好），不是用户自己点的。 */
    val followingDefault: Boolean,
)

/** 这个角色的 her 列表：能证明归属的都列，发布态在前，同类保持服务端顺序。 */
fun hersForRole(hers: List<HerSummary>, roleId: String?): List<HerSummary> {
    if (roleId.isNullOrBlank()) return emptyList()
    return hers
        .filter { roleKeyFromHerId(it.id, it.renderer) == roleId }
        .sortedByDescending { it.status == STATUS_PUBLISHED }
}

/** 这张卡现在能不能选。 */
fun herSelectability(her: HerSummary): HerSelectability {
    if (her.status != STATUS_PUBLISHED) return HerSelectability.NOT_PUBLISHED
    return if (stageChoiceForHer(her) == null) {
        HerSelectability.RENDERER_UNSUPPORTED
    } else {
        HerSelectability.SELECTABLE
    }
}

/**
 * 点这张卡 = 写哪条 [StageChoice]；不可选回 null（UI 就不挂点击）。
 *
 * `simple` 单独一条：它在 [engineIdForRenderer] 里回 null，含义是「用现在的默认舞台」
 * （分层立绘 / 圆头像），在列表页就是「原来的形象」——所以映射成 [StageChoice.Original]
 * 而不是「不可选」。其余 renderer 复用那张映射表，不另起一份，避免两处漂移。
 */
fun stageChoiceForHer(her: HerSummary): StageChoice? {
    if (her.status != STATUS_PUBLISHED) return null
    val renderer = her.renderer.trim().lowercase()
    if (renderer.isEmpty()) return null
    if (renderer == RENDERER_SIMPLE) return StageChoice.Original
    return engineIdForRenderer(renderer)?.let { StageChoice.Engine(it) }
}

/**
 * 当前哪张卡该描边。真源是 [StageChoice]（用户意愿）+ 服务端默认，不是 her id：
 * - [StageChoice.Unset]：跟随服务端默认那一张（与 [com.openapi.ks.myapp.dh.resolveCallStage]
 *   取默认 her 的口径一致：第一个发布态、且能证明属于这个角色）。哪怕这端接不了那个
 *   renderer，也照样标出来——用户该看到「服务端想让她长这样，但端上还做不到」；
 * - 显式选择：找第一张能兑现这个舞台的可选卡；这角色没有对应的 her 就谁也不标（不假装有选中）。
 */
fun herListSelection(choice: StageChoice, roleId: String?, hers: List<HerSummary>): HerListSelection {
    return when (choice) {
        StageChoice.Unset -> {
            val target = hers.firstOrNull {
                it.status == STATUS_PUBLISHED && roleKeyFromHerId(it.id, it.renderer) == roleId
            }
            HerListSelection(selectedHerId = target?.id, followingDefault = target != null)
        }
        else -> {
            val target = hers.firstOrNull { stageChoiceForHer(it) == choice }
            HerListSelection(selectedHerId = target?.id, followingDefault = false)
        }
    }
}

// ---- 卡片文案：服务端没真给过的数字，整段都不显示（不编） ----

/** 已发布 / 草稿 / 已下架；认不出的状态原样显示，不替服务端粉饰成「已发布」。 */
fun herStatusLabel(status: String): String = when (status.trim().lowercase()) {
    "published" -> "已发布"
    "draft" -> "草稿"
    "retired" -> "已下架"
    else -> status.trim()
}

/** `pack v3`；服务端没给版本号（0/缺失）就不显示这个标签。 */
fun herVersionLabel(her: HerSummary): String? =
    her.packVersion.takeIf { it > 0 }?.let { "pack v$it" }

/** 「4 个特效 · 2 张贴纸」；两个数都是 0/缺失就整行不显示。 */
fun herMetaLine(her: HerSummary): String? {
    val parts = listOfNotNull(
        her.effectCount.takeIf { it > 0 }?.let { "$it 个特效" },
        her.stickerCount.takeIf { it > 0 }?.let { "$it 张贴纸" },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** 卡片标题：服务端给了名字就用，没给就退回 her id（可见的原始标识，不编名字）。 */
fun herDisplayName(her: HerSummary): String =
    her.name.trim().takeIf { it.isNotEmpty() } ?: her.id

private const val STATUS_PUBLISHED = "published"
private const val RENDERER_SIMPLE = "simple"

/** renderer 的展示标签：认得出的说人话，认不出原样显示（与状态同一口径）。 */
fun herRendererLabel(renderer: String): String = when (renderer.trim().lowercase()) {
    "simple" -> "原来的形象"
    "live2d" -> "Live2D"
    "vrm" -> "VRM"
    "spine" -> "Spine"
    else -> renderer.trim()
}

/** 不可选时卡片上的说明；可选回 null（不画说明）。 */
fun herLockedReason(her: HerSummary): String? = when (herSelectability(her)) {
    HerSelectability.SELECTABLE -> null
    HerSelectability.NOT_PUBLISHED -> "草稿：还不能选"
    HerSelectability.RENDERER_UNSUPPORTED -> "这个形象端上还没接入"
}

/** 选中态的角标：用户点的 vs 服务端默认的。 */
fun herSelectionBadge(followingDefault: Boolean): String =
    if (followingDefault) "默认跟随" else "使用中"
