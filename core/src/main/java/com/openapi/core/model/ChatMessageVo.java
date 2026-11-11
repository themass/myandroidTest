package com.openapi.core.model;

/**
 * 服务端会话里的一条消息（`/api/chat/messages.json`、`/message/edit.json`）。
 * [role] 为 "user" / "assistant"；[type] 与 ChatLogMapper 的 TYPE_* 一致（0 文本）。
 * createdAt 为毫秒时间戳。
 */
public class ChatMessageVo {
    public long id;
    public long sessionId;
    public String role;
    public String content;
    public int type;
    public String clientMsgId;
    public long createdAt;
}
