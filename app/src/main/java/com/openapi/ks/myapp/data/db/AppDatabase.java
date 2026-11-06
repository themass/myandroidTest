package com.openapi.ks.myapp.data.db;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.SQLite;
import androidx.sqlite.SQLiteConnection;

import com.openapi.core.event.FavoriteVo;
import com.openapi.ks.myapp.bean.form.ChatLog;
import com.openapi.ks.myapp.bean.form.ChatSessionLog;
import com.openapi.ks.myapp.bean.form.ConnLog;
import com.openapi.ks.myapp.bean.vo.HistoryVo;

@Database(
        entities = {ChatLog.class, ChatSessionLog.class, ConnLog.class, HistoryVo.class, FavoriteVo.class},
        version = 2,
        exportSchema = false
)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {

    /**
     * 版本 1 → 2：加入收藏表（原来在 commonutil 的独立库 favorites.db 里）。
     * 只建新表，不动聊天、连接日志、历史等已有数据。SQL 与 Room 为 FavoriteVo 生成的建表语句一致，
     * 否则 Room 打开时会校验失败。
     */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SQLiteConnection connection) {
            SQLite.execSQL(connection, "CREATE TABLE IF NOT EXISTS `FavoriteVo` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `name` TEXT, `updateTime` TEXT, `type` INTEGER NOT NULL, `itemUrl` TEXT, `extra` TEXT)");
            SQLite.execSQL(connection, "CREATE UNIQUE INDEX IF NOT EXISTS `index_FavoriteVo_itemUrl` ON `FavoriteVo` (`itemUrl`)");
        }
    };

    public abstract ChatLogDao chatLogDao();

    public abstract ChatSessionLogDao chatSessionLogDao();

    public abstract ConnLogDao connLogDao();

    public abstract HistoryDao historyDao();

    public abstract FavoriteDao favoriteDao();
}
