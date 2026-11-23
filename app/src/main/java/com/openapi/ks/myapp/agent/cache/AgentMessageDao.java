package com.openapi.ks.myapp.agent.cache;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AgentMessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(List<AgentMessageEntity> messages);

    /** 只读取最近的 limit 条（按 createdSeq 倒序取，再由调用方翻转为时间顺序）。 */
    @Query("SELECT * FROM agent_message WHERE session_id = :sessionId ORDER BY created_seq DESC LIMIT :limit")
    List<AgentMessageEntity> latest(String sessionId, int limit);

    /** 整窗替换：保存时先删除该会话的旧窗口，再写入新窗口，保证顺序准确。 */
    @Query("DELETE FROM agent_message WHERE session_id = :sessionId")
    void deleteSession(String sessionId);

    @Query("DELETE FROM agent_message")
    void clearAll();
}
