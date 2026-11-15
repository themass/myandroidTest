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
        )
    }
}

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
