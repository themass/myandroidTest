package com.openapi.core.model;

import androidx.annotation.NonNull;

/**
 * 一条命中消息的搜索快照（整改清单 P2-15）：消息本身只存在 ChatLog 里，
 * 但界面上要显示「在哪个会话、那句话长什么样」，所以查询直接联出会话名和预览。
 * 只用于查询结果，不是表，不需要 @Entity。
 */
public class ChatMessageHit {
    @NonNull public Long sessionId;
    @NonNull public String sessionName;
    /** 命中消息的开头片段；完整正文太长，列表里放不下。 */
    @NonNull public String preview;
}
