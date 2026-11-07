package com.openapi.ks.myapp.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.openapi.ks.myapp.bean.form.ChatSessionLog;

import java.util.List;

/**
 * 会话列表的本地缓存 DAO。权威数据在服务端（`/api/chat/sessions.json`），这里只做离线展示。
 */
@Dao
public interface ChatSessionLogDao {

    /** 同一会话（服务端 id 相同）覆盖旧记录。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertOrReplace(ChatSessionLog log);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplaceAll(List<ChatSessionLog> logs);

    @Query("SELECT * FROM ChatSessionLog WHERE id = :id")
    ChatSessionLog load(long id);

    /** 按服务端 updatedAt 倒序，与服务端会话列表的顺序一致。 */
    @Query("SELECT * FROM ChatSessionLog ORDER BY updatedAt DESC, id DESC")
    List<ChatSessionLog> loadAll();

    @Query("DELETE FROM ChatSessionLog WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM ChatSessionLog")
    void deleteAll();
}
