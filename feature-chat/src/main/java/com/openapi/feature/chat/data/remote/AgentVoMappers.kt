package com.openapi.feature.chat.data.remote

import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.ChatSession
import com.openapi.feature.chat.domain.model.ImageAttachment
import com.openapi.feature.chat.domain.model.ImageSource
import com.openapi.feature.chat.domain.model.MessageKind
import java.time.OffsetDateTime

/**
 * `/v1` 的 VO → 领域模型映射（整改清单 P3-17：从 AgentBackedChatRepository 拆出）。
 * 独立文件不引入新行为：仓库类只留「编排」，映射规则可单独读懂、单独测试。
 */
internal fun AgentMessageVo.toChatMessage(): ChatMessage {
    // 历史里的图片只带 upload_id（字节在对象存储）。先挂成 REMOTE 形态，
    // loadHistoryPage 再逐个换签名 URL；有图就把 kind 判成 IMAGE，
    // 本地缓存才有富类型可保（TEXT 行会被刷新清掉）。
    val images = attachments.filter { it.kind == "image" }.map {
        ImageAttachment(path = "", source = ImageSource.REMOTE, uploadId = it.uploadId)
    }
    return ChatMessage(
        id = id.toString(),
        role = if (role == "user") ChatRole.USER else ChatRole.ASSISTANT,
        text = content,
        createdAtMillis = parseIso(createdAt),
        kind = if (images.isNotEmpty()) MessageKind.IMAGE else MessageKind.TEXT,
        images = images,
        clientMessageId = clientMessageId,
    )
}

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
