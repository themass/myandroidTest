package com.openapi.ks.myapp.data;

import android.content.Context;

import androidx.room.Room;

import com.openai.commonutil.LogUtil;
import com.openapi.ks.myapp.bean.form.ChatLog;
import com.openapi.ks.myapp.data.db.AppDatabase;
import com.openapi.ks.myapp.data.db.ChatLogDao;
import com.openapi.ks.myapp.data.db.ChatSessionLogDao;
import com.openapi.ks.myapp.data.db.ConnLogDao;
import com.openapi.ks.myapp.data.db.HistoryDao;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 应用数据库入口（Room）：聊天记录、会话、连接日志、浏览历史。
 *
 * 库名换成了 app.db：旧的 greenDAO 库（freeSEX）不再读取，也不迁移。
 * 旧代码（聊天页、会话列表、启动页）会在主线程直接读写，greenDAO 不限制这一点，
 * 所以这里允许主线程查询以保持原行为；新代码应放到后台线程。
 */
public class DBManager {

    public static final String DB_NAME = "app.db";
    /** 聊天记录只保留最近这么多条，沿用旧逻辑。 */
    private static final long CHAT_LOG_KEEP = 30;

    //多线程中要被共享的使用volatile关键字修饰
    private volatile static DBManager manager = new DBManager();
    private final ExecutorService trimExecutor = Executors.newSingleThreadExecutor();
    private volatile AppDatabase database;
    private Context context;

    public static DBManager getInstance() {
        return manager;
    }

    public void init(Context context) {
        this.context = context.getApplicationContext();
        LogUtil.i("chat log count ====" + chatLogDao().count());
    }

    private AppDatabase getDatabase() {
        AppDatabase db = database;
        if (db == null) {
            synchronized (this) {
                db = database;
                if (db == null) {
                    db = Room.databaseBuilder(context, AppDatabase.class, DB_NAME)
                            .allowMainThreadQueries()
                            .fallbackToDestructiveMigration()
                            .build();
                    database = db;
                }
            }
        }
        return db;
    }

    public ChatLogDao chatLogDao() {
        return getDatabase().chatLogDao();
    }

    public ChatSessionLogDao chatSessionLogDao() {
        return getDatabase().chatSessionLogDao();
    }

    public ConnLogDao connLogDao() {
        return getDatabase().connLogDao();
    }

    public HistoryDao historyDao() {
        return getDatabase().historyDao();
    }

    /** 关闭数据库，下次访问时重新打开。 */
    public synchronized void closeConnection() {
        if (database != null) {
            database.close();
            database = null;
        }
    }

    public void saveChatLog(ChatLog log) {
        chatLogDao().save(log);
        // 清理放到后台：不拖慢发消息
        trimExecutor.execute(() -> {
            try {
                chatLogDao().trimToLatest(CHAT_LOG_KEEP);
            } catch (Throwable e) {
                LogUtil.e(e);
            }
        });
    }
}
