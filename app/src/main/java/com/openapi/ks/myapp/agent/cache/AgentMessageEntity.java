package com.openapi.ks.myapp.agent.cache;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** 一条已同步的消息。主键是服务端 message_id；本地发送中的消息不入库（客户端设计 9）。 */
@Entity(
        tableName = "agent_message",
        indices = {@Index(value = {"session_id", "created_seq"})}
)
public class AgentMessageEntity {
    @PrimaryKey
    @NonNull
    public String id = "";

    @NonNull
    @ColumnInfo(name = "session_id")
    public String sessionId = "";

    @NonNull
    public String role = "";

    @NonNull
    public String content = "";

    /** 服务端时间顺序的近似值：按插入顺序递增，用于读取最近 N 条。 */
    @ColumnInfo(name = "created_seq")
    public long createdSeq;
}
