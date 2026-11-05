package com.openai.manage;

import android.content.Context;

import androidx.room.Room;

/**
 * 收藏库的入口（Room）。
 *
 * 库名换成了 favorites.db：旧的 greenDAO 库（favi）不再读取，也不迁移。
 * 旧代码可能在主线程直接读写收藏（greenDAO 不限制），所以这里允许主线程查询，
 * 保持原行为；新代码仍应放到后台线程。
 */
public class DBManager {

    public static final String DB_NAME = "favorites.db";
    //多线程中要被共享的使用volatile关键字修饰
    private volatile static DBManager manager = new DBManager();
    private volatile FavoriteDatabase database;
    private Context context;

    public static DBManager getInstance() {
        return manager;
    }

    public void init(Context context) {
        this.context = context.getApplicationContext();
    }

    public FavoriteDao favoriteDao() {
        return getDatabase().favoriteDao();
    }

    private FavoriteDatabase getDatabase() {
        FavoriteDatabase db = database;
        if (db == null) {
            synchronized (this) {
                db = database;
                if (db == null) {
                    db = Room.databaseBuilder(context, FavoriteDatabase.class, DB_NAME)
                            .allowMainThreadQueries()
                            .fallbackToDestructiveMigration()
                            .build();
                    database = db;
                }
            }
        }
        return db;
    }

    /** 关闭数据库，下次访问时重新打开。 */
    public synchronized void closeConnection() {
        if (database != null) {
            database.close();
            database = null;
        }
    }
}
