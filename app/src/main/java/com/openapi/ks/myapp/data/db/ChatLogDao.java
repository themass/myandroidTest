package com.openapi.ks.myapp.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.openapi.ks.myapp.bean.form.ChatLog;

import java.util.List;

@Dao
public interface ChatLogDao {

    /** 同一条消息（chatId 相同）再次保存时覆盖旧记录，而不是抛唯一约束异常。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long save(ChatLog log);

    @Query("SELECT * FROM ChatLog WHERE sessionId = :sessionId ORDER BY id ASC")
    List<ChatLog> bySession(long sessionId);

    @Query("DELETE FROM ChatLog WHERE chatId = :chatId")
    void deleteByChatId(String chatId);

    @Query("DELETE FROM ChatLog")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM ChatLog")
    long count();

    /** 只保留最近 [keep] 条：删掉 id 比最大 id 小 [keep] 以上的记录。 */
    @Query("DELETE FROM ChatLog WHERE id <= (SELECT MAX(id) FROM ChatLog) - :keep")
    void trimToLatest(long keep);
}
