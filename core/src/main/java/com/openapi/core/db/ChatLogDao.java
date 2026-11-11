package com.openapi.core.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.openapi.core.model.ChatLog;

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

    /** 删除某个会话的全部缓存（会话被删除，或整页刷新前清掉旧缓存）。 */
    @Query("DELETE FROM ChatLog WHERE sessionId = :sessionId")
    void deleteBySession(long sessionId);

    @Query("DELETE FROM ChatLog")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM ChatLog")
    long count();

    /**
     * 只保留最近 [keep] 条纯文字缓存：删掉 id 比最大 id 小 [keep] 以上的文字记录。
     * 语音、图片、文件（msgType != 0）和本地临时消息（chatId 以 local- 开头）不删：
     * 服务端历史里只有文字，这些记录里的附件信息只存在本机，删了就找不回来了。
     */
    @Query("DELETE FROM ChatLog WHERE msgType = 0 AND chatId NOT LIKE 'local-%' "
            + "AND id <= (SELECT MAX(id) FROM ChatLog) - :keep")
    void trimToLatest(long keep);

    @Query("SELECT * FROM ChatLog WHERE chatId = :chatId LIMIT 1")
    ChatLog byChatId(String chatId);
}
