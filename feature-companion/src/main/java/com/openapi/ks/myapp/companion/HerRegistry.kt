package com.openapi.ks.myapp.companion

import org.json.JSONObject

/**
 * her 注册表摘要（服务端第一刀 `ca_her_pack`，`GET /v1/hers`）。
 * 呈现实体（renderer + 模型包），不是 persona——见 2026-11-28 persona/her 分层架构 §2。
 * 解析与 [CharacterSummary] 同款纪律：opt* 读取，缺省字段退化成空值，绝不抛。
 */
data class HerSummary(
    val id: String,
    val name: String,
    val tagline: String,
    /** 引擎判别长在这个实体上（不是角色上）：simple / live2d（未来值）。 */
    val renderer: String,
    /** 服务端子集状态：published 才会驱动端上选择。 */
    val status: String,
    val packVersion: Int = 0,
    val effectCount: Int = 0,
    val stickerCount: Int = 0,
    /** her 包默认音色的 voice_id（音色优先级：用户 her 覆盖 > her 包 > persona 卡）。 */
    val voiceId: String? = null,
) {
    companion object {
        fun from(json: JSONObject) = HerSummary(
            id = json.optString("id"),
            name = json.optString("name"),
            tagline = json.optString("tagline"),
            renderer = json.optString("renderer"),
            status = json.optString("status"),
            packVersion = json.optInt("pack_version"),
            effectCount = json.optInt("effect_count"),
            stickerCount = json.optInt("sticker_count"),
            voiceId = json.optJSONObject("voice_ref")
                ?.optString("voice_id")?.trim()?.takeIf { it.isNotEmpty() },
        )
    }
}

data class HerPage(val items: List<HerSummary>, val total: Int)
