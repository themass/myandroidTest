package com.openapi.chat.data.remote

/*
 * 请求参数。BaseService.postData 通过字段反射把对象序列化成表单，所以字段必须是裸的 public 字段
 * （@JvmField），并且所有字段都非空，否则会序列化出字面量 "null" 或静默丢字段（见 BeanUtil.transBean2Map）。
 */

internal class CharacterCreateParam(
    @JvmField val title: String,
    @JvmField val content: String,
    @JvmField val url: String,
)

internal class CharacterUpdateParam(
    @JvmField val id: Int,
    @JvmField val title: String,
    @JvmField val content: String,
    @JvmField val url: String,
)

internal class CharacterDeleteParam(
    @JvmField val id: Int,
)

internal class SpeechParam(
    @JvmField val id: String,
    @JvmField val content: String,
)

internal class AttachmentParam(
    @JvmField val id: String,
    @JvmField val sessionId: Long,
)

/** 最新一页消息：不带 beforeId（[MessagesOlderPageParam] 才带），避免 null 被序列化成字面量 "null"。 */
internal class MessagesFirstPageParam(
    @JvmField val sessionId: Long,
    @JvmField val limit: Int,
)

internal class MessagesOlderPageParam(
    @JvmField val sessionId: Long,
    @JvmField val beforeId: Long,
    @JvmField val limit: Int,
)

internal class SessionCreateParam(
    @JvmField val name: String,
)

internal class SessionRenameParam(
    @JvmField val id: Long,
    @JvmField val name: String,
)

internal class SessionIdParam(
    @JvmField val id: Long,
)

internal class MessageDeleteParam(
    @JvmField val sessionId: Long,
    @JvmField val id: Long,
)

internal class MessageEditParam(
    @JvmField val sessionId: Long,
    @JvmField val id: Long,
    @JvmField val text: String,
)
