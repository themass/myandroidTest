package com.openapi.feature.chat.data.remote;

/**
 * 服务端会话列表项（`/api/chat/sessions.json` 等）。时间为毫秒时间戳。
 * 会话 id 由服务端生成，客户端不再自己分配。
 */
public class ChatSessionVo {
    public long id;
    public String name;
    public Integer characterId;
    public String roleId;
    public long createdAt;
    public long updatedAt;
    public String lastMessage;
    public long messageCount;
}
