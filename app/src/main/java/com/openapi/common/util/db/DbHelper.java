package com.openapi.common.util.db;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;


/**
 * Db基础类.
 *
 * @author wjying
 */
public class DbHelper {
    public static final String PARAMETER_NOTIFY = "notify";
    private static final String TAG = "DbHelper";
    private final SQLiteOpenHelper mOpenHelper;

    private final Context mContext;

    DbHelper(Context context, SQLiteOpenHelper helper) {
        mContext = context.getApplicationContext();
        mOpenHelper = helper;
    }

//    /**
//     * 子类应该通过本方法设置SQLiteOpenHelper
//     * 
//     * @param helper
//     */
//    public void setSQLiteOpenHelper(SQLiteOpenHelper helper) {
//        mOpenHelper = helper;
//    }

    public Context getContext() {
        return mContext;
    }
//
//    @Override
//    public String getType(Uri uri) {
//        SqlArguments args = new SqlArguments(uri, null, null);
//        if (TextUtils.isEmpty(args.where)) {
//            return "vnd.android.cursor.dir/" + args.table;
//        } else {
//            return "vnd.android.cursor.item/" + args.table;
//        }
//    }


    /**
     * 查询
     *
     * @param uri
     * @param projection
     * @param selection
     * @param selectionArgs
     * @param sortOrder
     * @return
     */
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {

        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(args.table);

        SQLiteDatabase db;
        Cursor result = null;
        try {
            db = mOpenHelper.getWritableDatabase();// .getReadableDatabase();
            result = qb.query(db, projection, args.where, args.args, null, null, sortOrder);
            if (result != null) {
                result.setNotificationUri(getContext().getContentResolver(), uri);
            }
        } catch (SQLiteDiskIOException e) {
            if (result != null) {
                result.close();
                result = null;
            }
        }

        return result;
    }

    /**
     * 插入
     *
     * @param uri
     * @param initialValues
     * @return
     */
    public Uri insert(Uri uri, ContentValues initialValues) {
        SqlArguments args = new SqlArguments(uri);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        final long rowId = db.insert(args.table, null, initialValues);
        if (rowId <= 0)
            return null;

        uri = ContentUris.withAppendedId(uri, rowId);
        sendNotify(uri);

        return uri;
    }

    /**
     * 批量插入
     *
     * @param uri
     * @param values
     * @return
     */
    public int bulkInsert(Uri uri, ContentValues[] values) {
        SqlArguments args = new SqlArguments(uri);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (ContentValues val : values) {
                if (db.insert(args.table, null, val) < 0)
                    return 0;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        sendNotify(uri);
        return values.length;
    }

    /**
     * 删除
     *
     * @param uri
     * @param selection
     * @param selectionArgs
     * @return
     */
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count = 0;
        try {
            count = db.delete(args.table, args.where, args.args);
        } catch (SQLiteDiskIOException ignored) {
        }
        // if (count > 0) sendNotify(uri);
        sendNotify(uri); // TODO 为什么对整个表操作返回count=0
        return count;
    }

    /**
     * 更新
     *
     * @param uri
     * @param values
     * @param selection
     * @param selectionArgs
     * @return
     */
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        int count = 0;
        try {
            count = db.update(args.table, values, args.where, args.args);
        } catch (SQLiteDiskIOException ignored) {
        }
        // if (count > 0) sendNotify(uri);
        sendNotify(uri); // TODO 为什么对整个表操作返回count=0
        return count;
    }

    private void sendNotify(Uri uri) {
        String notify = uri.getQueryParameter(PARAMETER_NOTIFY);
        if (notify == null || "true".equals(notify)) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
    }

    private static class SqlArguments {
        public final String table;

        public final String where;

        public final String[] args;

        SqlArguments(Uri url, String where, String[] args) {
            if (url.getPathSegments().size() == 1) {
                this.table = url.getPathSegments().get(0);
                this.where = where;
                this.args = args;
            } else if (url.getPathSegments().size() != 2) {
                throw new IllegalArgumentException("Invalid URI: " + url);
            } else if (!TextUtils.isEmpty(where)) {
                throw new UnsupportedOperationException("WHERE clause not supported: " + url);
            } else {
                this.table = url.getPathSegments().get(0);
                this.where = "_id=" + ContentUris.parseId(url);
                this.args = null;
            }
        }

        SqlArguments(Uri url) {
            if (url.getPathSegments().size() == 1) {
                table = url.getPathSegments().get(0);
                where = null;
                args = null;
            } else {
                throw new IllegalArgumentException("Invalid URI: " + url);
            }
        }
    }
}
