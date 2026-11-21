package com.openapi.ks.myapp.companion

import org.json.JSONObject

/**
 * 与服务端 companion 服务的接口约定。
 *
 * 服务端实现在 companion-agent 项目的 src/companion/server/ 下：
 * sessions.py（会话）、characters.py（角色）、memory_api.py（记忆与事件）、
 * control_api.py（动作确认与偏好）、inbox_api.py（主动消息）。
 */

data class SessionResponse(
    val sessionId: String,
    val roomName: String,
    val token: String,
    val livekitUrl: String,
    val expiresAt: String,
    /**
     * 这段语音房间绑定的**文字聊天会话** id（`/v1/sessions/{id}/messages` 用的那个）。
     *
     * 和 [sessionId] 不是一回事：后者是 LiveKit 房间凭据。服务端 `resolve_chat_session()`
     * 保证「一个用户 + 一个角色只有一段历史，语音和文字都写进它」，所以拿这个 id 就能
     * 把之前说过的话读回来——在此之前陪伴页关掉重开是空的，而历史其实一直写进去了。
     *
     * null 表示服务端没绑定聊天会话（老版本或未登录），此时不做历史回放。
     */
    val chatSessionId: Long? = null,
    /**
     * 这段通话走哪条 RTC 线路：`livekit`（默认）| `volcengine`。
     *
     * 权威在服务端——建会话时定，随 grant 下发；客户端不做探测也不自选，只照着
     * [CompanionTransports] 分发（docs/companion-rtc-provider-switching.md §2）。
     * 空串按 livekit 解释，所以老服务端/新客户端、新服务端/老客户端都不断。
     */
    val provider: String = CompanionTransports.PROVIDER_LIVEKIT,
    /** provider 特定参数（火山线的 app_id/room_id/token 等）；livekit 时为 null。原样透传，由各家实现自己解读。 */
    val providerConfig: JSONObject? = null,
)

// ---- 历史 ----

/**
 * 历史里的一条消息。只有 role 与 text：陪伴页不渲染附件，图片/文件那种消息
 * 在「对话」页看更合适，这里读到就跳过，不当作错误。
 */
data class CompanionHistoryItem(val role: String, val text: String) {
    val isUser: Boolean get() = role == "user"
}

// ---- 角色 ----

data class CharacterSummary(
    val id: String,
    val name: String,
    val tagline: String,
    val tags: List<String>,
    val voiceAvailable: Boolean,
    /**
     * 这个角色的声音的人话名字（如「沉稳男声」），通话页顶栏用来告诉用户「声音跟形象走」。
     * 服务端按 voice_ref 下发；没下发或为空就是 null，界面不显示声音标签（不编造）。
     */
    val voiceLabel: String? = null,
) {
    companion object {
        fun from(json: JSONObject) = CharacterSummary(
            id = json.optString("id"),
            name = json.optString("name"),
            tagline = json.optString("tagline"),
            tags = json.optJSONArray("tags")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it) }
            }.orEmpty(),
            voiceAvailable = json.optBoolean("voice_available", true),
            voiceLabel = (json.optString("voice_label").ifBlank { json.optString("voice_name") })
                .trim().ifBlank { null }
                ?: voiceRefLabel(json),
        )

        /**
         * 服务端 voice_ref：`{"provider":"volcengine","voice_id":"..."}`。位置优先级：
         * 顶层（外部导入的 V2 卡）> data 顶层 > extensions.vvv > data.extensions.vvv。
         * resource_id=seed-tts-2.0 下没有授权男声，所以文案绝不写「男声/女声」，
         * 只写音色中文名；不认识的 id 原样展示。
         */
        private fun voiceRefLabel(json: JSONObject): String? {
            val data = json.optJSONObject("data")
            val ref = json.optJSONObject("voice_ref")
                ?: data?.optJSONObject("voice_ref")
                ?: json.optJSONObject("extensions")?.optJSONObject("vvv")?.optJSONObject("voice_ref")
                ?: data?.optJSONObject("extensions")?.optJSONObject("vvv")?.optJSONObject("voice_ref")
                ?: return null
            val id = ref.optString("voice_id").trim().ifBlank { return null }
            return VOLC_VOICE_NAMES[id] ?: id
        }

        private val VOLC_VOICE_NAMES = mapOf(
            "saturn_zh_female_tiexinnvyou_tob" to "贴心女友",
            "saturn_zh_female_bingjiaojiejie_tob" to "病娇姐姐",
        )
    }
}

/** 顶栏声音标签文案；声音未知时返回 null，调用方不画标签。 */
fun callVoiceChipText(voiceLabel: String?): String? = voiceLabel?.trim()?.takeIf { it.isNotEmpty() }

data class CharacterPage(val items: List<CharacterSummary>, val total: Int)

data class CharacterDetail(
    val summary: CharacterSummary,
    val description: String,
    val personality: String,
    val scenario: String,
    val firstMessage: String,
    val alternateGreetings: List<String>,
    val skill: String,
) {
    companion object {
        fun from(json: JSONObject) = CharacterDetail(
            summary = CharacterSummary.from(json),
            description = json.optString("description"),
            personality = json.optString("personality"),
            scenario = json.optString("scenario"),
            firstMessage = json.optString("first_mes"),
            alternateGreetings = json.optJSONArray("alternate_greetings")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it) }
            }.orEmpty(),
            skill = json.optString("skill"),
        )
    }
}

// ---- 记忆（事实）与时间线事件：两者是不同的存储，界面上也分开展示 ----

data class MemoryFact(
    val id: String,
    val content: String,
    val kind: String,
    val sourceCharacterId: String,
    val createdAt: String,
) {
    companion object {
        fun from(json: JSONObject) = MemoryFact(
            id = json.optString("id"),
            content = json.optString("content"),
            kind = json.optString("kind"),
            sourceCharacterId = json.optString("source_character_id"),
            createdAt = json.optString("created_at"),
        )
    }
}

data class FactPage(val items: List<MemoryFact>, val hasMore: Boolean)

/**
 * 她对你的了解（服务端归纳出来的画像，和一条条记忆分开）。
 * [isInference] 为真表示是她的猜测，没经你确认；界面要标出来，别当成事实展示。
 */
data class ProfileEntry(
    val id: String,
    val topic: String,
    val memo: String,
    val section: String,
    val isInference: Boolean,
    val suppressed: Boolean,
    val supersededBy: String?,
) {
    /** 当前有效：没被新条目取代，也没被你说「别再提」。 */
    val current: Boolean get() = supersededBy == null && !suppressed

    companion object {
        fun from(json: JSONObject) = ProfileEntry(
            id = json.get("id").toString(),
            topic = json.optString("topic"),
            memo = json.optString("memo"),
            section = json.optString("section", "static"),
            isInference = json.optBoolean("is_inference", false),
            suppressed = json.optBoolean("suppressed", false),
            supersededBy = if (json.isNull("superseded_by") || !json.has("superseded_by")) null
            else json.get("superseded_by").toString(),
        )
    }
}

data class TimelineEventItem(
    val id: String,
    val title: String,
    val kind: String,
    val at: String,
    val status: String,
    val confidence: Double,
) {
    companion object {
        fun from(json: JSONObject) = TimelineEventItem(
            id = json.optString("id"),
            title = json.optString("title"),
            kind = json.optString("kind"),
            at = json.optString("at"),
            status = json.optString("status"),
            confidence = json.optDouble("confidence", 0.0),
        )
    }
}

// ---- 动作确认 ----

data class PendingAction(
    val confirmationId: String,
    val characterId: String,
    val action: String,
    val description: String,
) {
    companion object {
        fun from(json: JSONObject) = PendingAction(
            confirmationId = json.optString("confirmation_id"),
            characterId = json.optString("character_id"),
            action = json.optString("action"),
            description = json.optString("description"),
        )
    }
}

// ---- 偏好：她能不能主动开口，全部可关 ----

data class CompanionPreferences(
    val eventsEnabled: Boolean,
    val greetingsEnabled: Boolean,
    val callEnabled: Boolean,
    val quietStart: String,
    val quietEnd: String,
    val maxPerDay: Int,
    val maxPerWeek: Int,
    val pausedUntil: String?,
) {
    companion object {
        fun from(json: JSONObject) = CompanionPreferences(
            eventsEnabled = json.optBoolean("events_enabled", true),
            greetingsEnabled = json.optBoolean("greetings_enabled", false),
            callEnabled = json.optBoolean("call_enabled", false),
            quietStart = json.optString("quiet_start", "23:00"),
            quietEnd = json.optString("quiet_end", "08:00"),
            maxPerDay = json.optInt("max_per_day", 2),
            maxPerWeek = json.optInt("max_per_week", 6),
            pausedUntil = json.optString("paused_until").takeIf { it.isNotBlank() && it != "null" },
        )
    }
}

// ---- 主动消息收件箱 ----

data class InboxMessage(
    val id: String,
    val characterId: String,
    val text: String,
    val channel: String,
    val at: String,
    /** 服务端给的触发原因：event_due / schedule 等，决定界面怎么标注「她为什么找你」。 */
    val trigger: String,
) {
    companion object {
        fun from(json: JSONObject) = InboxMessage(
            id = json.optString("id"),
            characterId = json.optString("character_id"),
            text = json.optString("text"),
            channel = json.optString("channel"),
            at = json.optString("at"),
            trigger = json.optString("trigger"),
        )
    }
}
