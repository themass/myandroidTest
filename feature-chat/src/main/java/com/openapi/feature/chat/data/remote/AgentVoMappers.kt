package com.openapi.feature.chat.data.remote

import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.ChatSession
import com.openapi.feature.chat.domain.model.GeoLocation
import com.openapi.feature.chat.domain.model.ImageAttachment
import com.openapi.feature.chat.domain.model.ImageSource
import com.openapi.feature.chat.domain.model.MessageKind
import com.openapi.feature.chat.domain.model.VideoAttachment
import java.time.OffsetDateTime

/**
 * `/v1` 的 VO → 领域模型映射（整改清单 P3-17：从 AgentBackedChatRepository 拆出）。
 * 独立文件不引入新行为：仓库类只留「编排」，映射规则可单独读懂、单独测试。
 */
internal fun AgentMessageVo.toChatMessage(): ChatMessage {
    // 历史里的图片/视频只带 upload_id（字节在对象存储）。先挂成空路径的形态：
    // 图片在 loadHistoryPage 换签名 URL 填进 path，视频**不换**——播放 URL 只在点开那一刻
    // 现取（300 秒的 URL 存进缓存等于第二天打开会话是一段黑屏）。
    // 图片 kind 两种写法服务端都发过（"image"/"img"，A40 用户 2026-11-26）：
    // JSON 边界已归一（toAgentMessageVo），这里再认一次是因为 VO 也会从缓存/测试直接构造——
    // 少认一种就是整条消息的图被丢掉。与陪伴页 CompanionHistoryItem.from 同一条口径。
    val images = attachments.filter { it.kind in IMAGE_KINDS }.map {
        ImageAttachment(path = "", source = ImageSource.REMOTE, uploadId = it.uploadId)
    }
    val videos = attachments.filter { it.kind == "video" }.map {
        VideoAttachment(path = "", name = it.name, durationSeconds = it.durationSeconds ?: 0, uploadId = it.uploadId)
    }
    val location = location?.let { GeoLocation(latitude = it.lat, longitude = it.lng, accuracyMeters = it.accuracyMeters) }
    // 有富内容就把 kind 判成富类型，本地缓存才有富类型可保（TEXT 行会被刷新清掉）。
    // 位置优先于图片/视频：服务端把位置消息存成 kind=="location"，一行最多一个 location 对象。
    val kind = when {
        location != null -> MessageKind.LOCATION
        images.isNotEmpty() -> MessageKind.IMAGE
        videos.isNotEmpty() -> MessageKind.VIDEO
        else -> MessageKind.TEXT
    }
    return ChatMessage(
        id = id.toString(),
        role = if (role == "user") ChatRole.USER else ChatRole.ASSISTANT,
        text = content,
        createdAtMillis = parseIso(createdAt),
        kind = kind,
        images = images,
        videos = videos,
        location = location,
        clientMessageId = clientMessageId,
        // 服务端标记的「这一轮被中断过」（半截回复）。以前解析了但在这里被丢掉，
        // 历史里一条半截回复和一条完整回复在端上就无法区分（任务：中断可见性）。
        interrupted = interrupted,
    )
}

/** 服务端图片附件的两种 kind 写法（A40）；新增写法只改这一处。 */
internal val IMAGE_KINDS = setOf("image", "img")

internal fun AgentSessionVo.toChatSession(): ChatSession = ChatSession(
    id = id,
    name = name.ifBlank { "新会话" },
    isDefault = false,
    roleId = roleId,
    characterId = null,
)

private fun parseIso(value: String): Long = runCatching {
    OffsetDateTime.parse(value).toInstant().toEpochMilli()
}.getOrDefault(System.currentTimeMillis())
