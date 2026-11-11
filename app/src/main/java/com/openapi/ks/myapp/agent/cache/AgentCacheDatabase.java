package com.openapi.ks.myapp.agent.cache;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/** 新对话缓存的数据库（独立文件 agent_chat_cache.db）。 */
@Database(entities = {AgentMessageEntity.class}, version = 1, exportSchema = false)
public abstract class AgentCacheDatabase extends RoomDatabase {

    public abstract AgentMessageDao messageDao();

    private static volatile AgentCacheDatabase instance;

    public static AgentCacheDatabase get(Context context) {
        if (instance == null) {
            synchronized (AgentCacheDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(), AgentCacheDatabase.class, "agent_chat_cache.db")
                            .build();
                }
            }
        }
        return instance;
    }
}
