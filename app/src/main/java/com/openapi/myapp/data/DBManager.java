package com.openapi.myapp.data;

import android.content.Context;


import com.openapi.myapp.bean.form.DaoMaster;
import com.openapi.myapp.bean.form.DaoSession;

import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.query.QueryBuilder;

/**
 * 创建数据库、创建数据库表、包含增删改查的操作以及数据库的升级
 * Created by Mr.sorrow on 2017/5/5.
 */

public class DBManager {

    public static final String DB_NAME = "GlobalVPN";
    //多线程中要被共享的使用volatile关键字修饰
    private volatile static DBManager manager = new DBManager();
    private static DaoMaster sDaoMaster;
    private static DaoMaster.DevOpenHelper sHelper;
    private static DaoSession sDaoSession;
    private Context context;

    /**
     * 单例模式获得操作数据库对象
     *
     * @return
     */
    public static DBManager getInstance() {
        return manager;
    }

    public void init(Context context) {
        this.context = context;
    }

    /**
     * 判断是否有存在数据库，如果没有则创建
     *
     * @return
     */
    public DaoMaster getDaoMaster() {
        if (sDaoMaster == null) {
            DaoMaster.OpenHelper helper = new KeepDataOpenHelper(context);
            sDaoMaster = new DaoMaster(helper.getWritableDatabase());
        }
        return sDaoMaster;
    }

    /**
     * 升级时只补建新增的表，不删除已有数据。
     * 默认的 DevOpenHelper 会删掉全部表，那样每次升级都会丢掉未上传的 ConnLog。
     */
    private static final class KeepDataOpenHelper extends DaoMaster.OpenHelper {
        KeepDataOpenHelper(Context context) {
            super(context, DB_NAME);
        }

        @Override
        public void onUpgrade(Database db, int oldVersion, int newVersion) {
            DaoMaster.createAllTables(db, true);
        }
    }

    /**
     * 完成对数据库的添加、删除、修改、查询操作，仅仅是一个接口
     *
     * @return
     */
    public DaoSession getDaoSession() {
        if (sDaoSession == null) {
            if (sDaoMaster == null) {
                sDaoMaster = getDaoMaster();
            }
            sDaoSession = sDaoMaster.newSession();
        }
        return sDaoSession;
    }

    /**
     * 打开输出日志，默认关闭
     */
    public void setDebug() {
        QueryBuilder.LOG_SQL = true;
        QueryBuilder.LOG_VALUES = true;
    }

    /**
     * 关闭所有的操作，数据库开启后，使用完毕要关闭
     */
    public void closeConnection() {
        closeHelper();
        closeDaoSession();
    }

    public void closeHelper() {
        if (sHelper != null) {
            sHelper.close();
            sHelper = null;
        }
    }

    public void closeDaoSession() {
        if (sDaoSession != null) {
            sDaoSession.clear();
            sDaoSession = null;
        }
    }
}