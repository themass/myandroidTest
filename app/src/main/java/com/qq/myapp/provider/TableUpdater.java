package com.qq.myapp.provider;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.sspacee.common.util.LogUtil;

/**
 * Created by wjying on 13-12-4.
 */
public class TableUpdater {
    private final static String COLUMN_TYPE_NTEXT = "NTEXT";
    private final static String COLUMN_TYPE_INTEGER = "INTEGER";
    private SQLiteDatabase mDb;
    private int mOldVersion;
    private int mNewVersion;
    private Context mContext;

    public TableUpdater(Context context, SQLiteDatabase db, int oldVersion, int newVersion) {
        mContext = context;
        mDb = db;
        mOldVersion = oldVersion;
        mNewVersion = newVersion;
    }

    public void execute() {
        int oldVersion = Math.max(mOldVersion, 79);
        if (null == mDb || mNewVersion < 1) {
            LogUtil.i("db is new");
        } else {
            execute(oldVersion);
        }
    }

    private void execute(int oldVersion) {
        switch (oldVersion) {
            default:
                break;
        }
    }

    /**
     * @param targetTable 目标操作表名
     * @param addColumn   新增字段名称
     * @param ColumnType  新增字段类型
     * @return 示例：ALTER TABLE NewsList ADD add_clumn NTEXT
     */
    private String alterSQLBuilder(String targetTable, String addColumn, String ColumnType) {
        return "ALTER TABLE " + targetTable + " ADD " + addColumn + " " + ColumnType;
    }
}
