package com.openapi.ks.myapp.dh

/**
 * 长按她之后的动作清单（纯函数，JVM 测试钉住）。
 *
 * 两类：
 * - **聊天通道动作**（enabled）：把 messageText 当用户发的文字送进对话，她以角色扮演
 *   的方式回话——与打字框同一条已验证的链路，不依赖模型能力，现在就能用。
 * - **真动作**（置灰）：转身、走动这些要驱动 Duix 动作区间（motionRegions），Oliver
 *   素材实测是空表，模型素材到位前不假装能点；置灰行带原因文案，点开只提示。
 */
data class AvatarAction(
    val id: String,
    val label: String,
    /** 走聊天通道的发送文案；置灰动作为 null。 */
    val messageText: String?,
    val enabled: Boolean,
    /** 置灰原因；可点动作为空串。 */
    val disabledReason: String = "",
)

fun avatarActions(): List<AvatarAction> = listOf(
    AvatarAction("pat_head", "摸摸头", "摸了摸她的头", enabled = true),
    AvatarAction("wave", "挥挥手", "朝她挥了挥手", enabled = true),
    AvatarAction("sit_close", "坐近点", "在她身边坐下", enabled = true),
    AvatarAction("turn", "转过身", null, enabled = false, disabledReason = "等模型动作素材"),
    AvatarAction("walk", "走过来", null, enabled = false, disabledReason = "等模型动作素材"),
    AvatarAction("nod", "点点头", null, enabled = false, disabledReason = "等模型动作素材"),
)
