package com.openapi.chat.domain.model

/** 一个对话会话。[isDefault] 为默认会话（老版本的 id=0 会话），列表里排在最前。 */
data class ChatSession(
    val id: Long,
    val name: String,
    val isDefault: Boolean,
)
