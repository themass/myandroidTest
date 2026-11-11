package com.openapi.ks.myapp.data.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.SQLite;
import androidx.sqlite.SQLiteConnection;

import com.openapi.core.event.FavoriteVo;
import com.openapi.common.util.LogUtil;
import com.openapi.ks.myapp.bean.form.ChatLog;
import com.openapi.ks.myapp.bean.form.ChatSessionLog;
import com.openapi.ks.myapp.bean.form.ConnLog;
import com.openapi.ks.myapp.bean.vo.HistoryVo;

/**
 * 应用数据库入口（Room）：聊天记录、会话、连接日志、浏览历史、收藏，全部在 app.db 里。
 *
 * 库名换成了 app.db：旧的 greenDAO 库（freeSEX）不再读取，也不迁移。
 * 版本 1 → 2 只加了收藏表（见 [#MIGRATION_1_2]），已有数据保留。
 * 旧代码（聊天页、会话列表、启动页）会在主线程直接读写，greenDAO 不限制这一点，
 * 所以这里允许主线程查询以保持原行为；新代码应放到后台线程。
 *
 * 生命周期：进程启动时由 MyApplication 调用一次 [#init(Context)]，之后用 [#getInstance()] 取库，
 * 首次取库时才真正打开 app.db。
 */
@Database(
        entities = {ChatLog.class, ChatSessionLog.class, ConnLog.class, HistoryVo.class, FavoriteVo.class},
        version = 3,
        exportSchema = false
)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {

    public static final String DB_NAME = "app.db";

    //多线程中要被共享的使用volatile关键字修饰
    private static volatile AppDatabase instance;
    private static volatile Context appContext;

    /** 应用启动时调用一次，保存 Application Context 并打印聊天记录条数。 */
    public static void init(Context context) {
        appContext = context.getApplicationContext();
        LogUtil.i("chat log count ====" + getInstance().chatLogDao().count());
    }

    /** 取得数据库，首次调用时用 [#init(Context)] 保存的 Context 创建。 */
    public static AppDatabase getInstance() {
        AppDatabase db = instance;
        if (db == null) {
            synchronized (AppDatabase.class) {
                db = instance;
                if (db == null) {
                    db = Room.databaseBuilder(appContext, AppDatabase.class, DB_NAME)
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .allowMainThreadQueries()
                            .fallbackToDestructiveMigration()
                            .build();
                    instance = db;
                }
            }
        }
        return db;
    }

    /** 关闭数据库，下次访问时重新打开。 */
    public static synchronized void closeConnection() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }

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

    /**
     * 版本 2 → 3：会话与消息改由服务端持有（见服务端 docs/chat-history-server.md）。
     * - ChatSessionLog 去掉 name 唯一索引（服务端允许重名），新增 updatedAt 列；
     * - 清空旧的本地聊天与会话缓存：旧记录的会话 id 是本地自增的，与服务端 id 对不上，
     *   留着会在离线时把别的会话内容显示出来。之后以服务端拉取为准。
     */
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SQLiteConnection connection) {
            SQLite.execSQL(connection, "DROP INDEX IF EXISTS `index_ChatSessionLog_name`");
            SQLite.execSQL(connection, "ALTER TABLE `ChatSessionLog` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0");
            SQLite.execSQL(connection, "DELETE FROM `ChatLog`");
            SQLite.execSQL(connection, "DELETE FROM `ChatSessionLog`");
        }
    };

    public abstract ChatLogDao chatLogDao();

    public abstract ChatSessionLogDao chatSessionLogDao();

    public abstract ConnLogDao connLogDao();

    public abstract HistoryDao historyDao();

    public abstract FavoriteDao favoriteDao();
}
