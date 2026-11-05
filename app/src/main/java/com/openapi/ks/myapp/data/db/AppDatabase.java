package com.openapi.ks.myapp.data.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.openapi.ks.myapp.bean.form.ChatLog;
import com.openapi.ks.myapp.bean.form.ChatSessionLog;
import com.openapi.ks.myapp.bean.form.ConnLog;
import com.openapi.ks.myapp.bean.vo.HistoryVo;

@Database(
        entities = {ChatLog.class, ChatSessionLog.class, ConnLog.class, HistoryVo.class},
        version = 1,
        exportSchema = false
)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {
    public abstract ChatLogDao chatLogDao();

    public abstract ChatSessionLogDao chatSessionLogDao();

    public abstract ConnLogDao connLogDao();

    public abstract HistoryDao historyDao();
}
