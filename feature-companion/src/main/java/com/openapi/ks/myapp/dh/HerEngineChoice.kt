package com.openapi.ks.myapp.dh

import com.openapi.ks.myapp.companion.HerSummary

/**
 * 引擎自动选择（客户端第二刀；persona/her 分层架构 §4「进房」her 半、§5 引擎注册表按
 * renderer 分发）。纯函数，JVM 钉死——UI 只消费结果，不改 UI 结构。
 *
 * renderer 长在 her 实体上而不是角色上（法务前提见 §6.4）；端上映射到 [DhEngineId]。
 * `simple` 就是**现状默认路径**（分层立绘 / 圆头像，`callStage == null` 那条），所以
 * 回 null 不是「降级」而是「就用原来的」——生产两行 her 都是 simple，第二刀落地后
 * 线上行为逐字节不变，管道为未来 live2d her 就绪。
 */
fun engineIdForRenderer(renderer: String?): DhEngineId? = when (renderer?.trim()?.lowercase()) {
    null, "" -> null
    "simple" -> null
    "live2d" -> DhEngineId.LIVE2D
    "vrm" -> DhEngineId.VRM
    "spine" -> DhEngineId.SPINE
    // 认不出的 renderer 一律回 null（= 现默认路径），不猜引擎——白屏比换错形象贵。
    else -> null
}

/**
 * her_id → 它绑定的角色（role_id）。播种规则（服务端第一刀）：`<renderer>-<role>-v<N>`。
 * 不合约定回 null：端上**不维护**角色↔her 对照表，绑定真源在服务端 `ca_character_her`，
 * 等它出 API 之前只按命名约定做可证的解析。
 */
fun roleKeyFromHerId(herId: String, renderer: String): String? {
    val prefix = renderer.trim().lowercase() + "-"
    if (!herId.startsWith(prefix)) return null
    val rest = herId.removePrefix(prefix)
    val match = VERSIONED_ROLE.matchEntire(rest) ?: return null
    return match.groupValues[1].takeIf { it.isNotBlank() }
}

private val VERSIONED_ROLE = Regex("""(.+)-v\d+""")

/** 只有发布态的包能驱动舞台；draft/retired 视同无 her。 */
private const val STATUS_PUBLISHED = "published"

/**
 * 通话舞台用哪个引擎——单一选择点（`CompanionRoute` 的 `avatarStage` 接它）：
 * 1. 用户显式选过（[StageChoice.Original] / [StageChoice.Engine]）：听用户的，服务端不覆盖；
 * 2. 没选过（[StageChoice.Unset]）：按该角色默认 her 的 renderer；
 * 3. 无 her / 未发布 / 未知 renderer：null = 现在的默认路径（分层立绘 / 圆头像）。
 */
fun resolveCallStage(
    choice: StageChoice,
    roleId: String?,
    hers: List<HerSummary>,
): DhEngineId? {
    return when (choice) {
        is StageChoice.Engine -> choice.id
        StageChoice.Original -> null
        StageChoice.Unset -> {
            if (roleId.isNullOrBlank()) return null
            val her = hers.firstOrNull {
                it.status == STATUS_PUBLISHED && roleKeyFromHerId(it.id, it.renderer) == roleId
            } ?: return null
            engineIdForRenderer(her.renderer)
        }
    }
}
