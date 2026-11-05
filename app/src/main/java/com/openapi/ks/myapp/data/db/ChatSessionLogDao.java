package com.openapi.ks.myapp.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.openapi.ks.myapp.bean.form.ChatSessionLog;

import java.util.List;

@Dao
public interface ChatSessionLogDao {

    /** 返回新行的 id；Room 不会像 greenDAO 那样把 id 回填进实体，调用方要自己用返回值。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertOrReplace(ChatSessionLog log);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplaceAll(List<ChatSessionLog> logs);

    /**
     * 写入"默认会话"，id 固定为 0。
     * 实体主键是 autoGenerate，Room 的 @Insert 遇到 id == 0 会当作"未设置"而重新分配 id，
     * 而旧逻辑把 0 当作默认会话的约定值，所以这里用显式 SQL 绕开自动分配。
     */
    @Query("INSERT OR REPLACE INTO ChatSessionLog (id, name, createTime, setting, type) "
            + "VALUES (0, :name, :createTime, :setting, :type)")
    void insertDefault(String name, Long createTime, String setting, int type);

    @Query("SELECT * FROM ChatSessionLog WHERE id = :id")
    ChatSessionLog load(long id);

    @Query("SELECT * FROM ChatSessionLog")
    List<ChatSessionLog> loadAll();

    @Query("DELETE FROM ChatSessionLog WHERE id = :id")
    void deleteById(long id);
}
