package com.sspacee.common.util.db;


import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;

/**
 * ContentProvider基础类.
 *
 * @author wjying
 */
public abstract class MyContentProvider extends ContentProvider {
    public static final String PARAMETER_NOTIFY = "notify";
    public static final String PARAMETER_RAW_QUERY = "raw_query";
    public static final String PARAMETER_RAW_SQL = "raw_sql";
    public static final String PARAMETER_NOTIFY_URI = "notify_uri";
    private static final String TAG = "MyContentProvider";
    private SQLiteOpenHelper mOpenHelper;

    private static Uri getNotifyUri(Uri uri) {
        if (uri == null) {
            return null;
        }
        Uri notifyUri = null;
        String notifyUriStr = uri.getQueryParameter(PARAMETER_NOTIFY_URI);
        if (!TextUtils.isEmpty(notifyUriStr)) {
            notifyUri = Uri.parse(notifyUriStr);
        }
        return notifyUri;
    }

    /**
     * 子类应该通过本方法设置SQLiteOpenHelper
     *
     * @param helper
     */
    public void setSQLiteOpenHelper(SQLiteOpenHelper helper) {
        mOpenHelper = helper;
    }

    public void close() {
        mOpenHelper.close();
    }

    @Override
    public String getType(Uri uri) {
        SqlArguments args = new SqlArguments(uri, null, null);
        if (TextUtils.isEmpty(args.where)) {
            return "vnd.android.cursor.dir/" + args.table;
        } else {
            return "vnd.android.cursor.item/" + args.table;
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        RawSql rawSql = RawSql.createRawSql(uri);
        if (rawSql != null) {
            return queryRAW(uri, rawSql.rawsql, selectionArgs);
        }
        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(args.table);

        SQLiteDatabase db;
        Cursor result = null;
        try {
            db = mOpenHelper.getWritableDatabase();// .getReadableDatabase();
            result = qb.query(db, projection, args.where, args.args, null, null, sortOrder);
            if (result != null) {
                Uri notifyUri = getNotifyUri(uri);
                result.setNotificationUri(getContext().getContentResolver(), notifyUri == null ? uri : notifyUri);
            }
        } catch (Exception e) {
            if (result != null) {
                result.close();
                result = null;
            }
        }

        return result;
    }

    private Cursor queryRAW(Uri uri, String rawSql, String[] selectionArgs) {
        SQLiteDatabase db;
        Cursor result = null;
        try {
            db = mOpenHelper.getWritableDatabase();// .getReadableDatabase();
            result = db.rawQuery(rawSql, selectionArgs);
            if (result != null) {
                Uri notifyUri = getNotifyUri(uri);
                result.setNotificationUri(getContext().getContentResolver(), notifyUri == null ? uri : notifyUri);
            }
        } catch (Exception e) {
            if (result != null) {
                result.close();
                result = null;
            }
        }
        return result;
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        SqlArguments args = new SqlArguments(uri);

        try {
            SQLiteDatabase db = mOpenHelper.getWritableDatabase();
            final long rowId = db.insert(args.table, null, initialValues);
            if (rowId <= 0)
                return null;

            uri = ContentUris.withAppendedId(uri, rowId);
            sendNotify(uri);
        } catch (Exception ignored) {
        }
        return uri;
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        SqlArguments args = new SqlArguments(uri);

        SQLiteDatabase db = null;
        try {
            db = mOpenHelper.getWritableDatabase();
            db.beginTransaction();
            for (ContentValues val : values) {
                if (db.insert(args.table, null, val) < 0)
                    return 0;
            }
            db.setTransactionSuccessful();
        } catch (Exception ignored) {
        } finally {
            if (db != null && db.inTransaction()) {
                db.endTransaction();
            }
        }
        sendNotify(uri);
        return values.length;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);

        int count = 0;
        try {
            SQLiteDatabase db = mOpenHelper.getWritableDatabase();
            count = db.delete(args.table, args.where, args.args);
            // if (count > 0) sendNotify(uri);
            sendNotify(uri); // TODO 为什么对整个表操作返回count=0
        } catch (Exception ignored) {
        }

        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);

        int count = 0;
        try {
            SQLiteDatabase db = mOpenHelper.getWritableDatabase();

            RawSql rawSql = RawSql.createRawSql(uri);
            if (rawSql != null) {
                db.execSQL(rawSql.rawsql);
            } else {
                count = db.update(args.table, values, args.where, args.args);
            }
            // if (count > 0) sendNotify(uri);
            sendNotify(uri); // TODO 为什么对整个表操作返回count=0
        } catch (Exception ignored) {
        }

        return count;
    }

    private void sendNotify(Uri uri) {
        String notify = uri.getQueryParameter(PARAMETER_NOTIFY);
        if (notify == null || "true".equals(notify)) {
            Uri notifyUri = getNotifyUri(uri);
            getContext().getContentResolver().notifyChange(notifyUri == null ? uri : notifyUri, null);
        }
    }

    private static class RawSql {
        final String rawsql;

        RawSql(String rawsql) {
            this.rawsql = rawsql;
        }

        static RawSql createRawSql(Uri url) {
            String rawsql = url.getQueryParameter(PARAMETER_RAW_QUERY);
            if (TextUtils.isEmpty(rawsql)) {
                rawsql = url.getQueryParameter(PARAMETER_RAW_SQL);
            }

            if (TextUtils.isEmpty(rawsql)) {
                return null;
            } else {
                return new RawSql(rawsql);
            }
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
