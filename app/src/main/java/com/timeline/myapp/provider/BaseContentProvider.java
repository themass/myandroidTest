package com.timeline.myapp.provider;

import android.net.Uri;
import android.text.TextUtils;

import com.sspacee.common.util.db.MyContentProvider;


public class BaseContentProvider extends MyContentProvider {

    public static final String AUTHORITY = "com.timeline.sexfree1";

    private static final String BASE_URI = "content://" + AUTHORITY + "/";

    /**
     * 返回表的URI
     *
     * @param table
     * @return
     */
    public static Uri getTableUri(String table) {
        return Uri.parse(BASE_URI + table);
    }

    /**
     * 返回表的URI,notify参数为false,不会通知数据库更新
     *
     * @param table
     * @return
     */
    public static Uri getTableUriNoNotify(String table) {
        return getTableUri(table).buildUpon().appendQueryParameter(PARAMETER_NOTIFY, "false").build();
    }

    /**
     * 返回sql查询uri
     *
     * @param sql
     * @return
     */
    public static Uri getRawQueryUri(String table, String sql) {
        return getTableUri(table).buildUpon().appendQueryParameter(PARAMETER_RAW_QUERY, sql).build();
    }

    /**
     * 返回sql执行语句
     *
     * @param sql
     * @return
     */
    public static Uri getRawSqlUri(String table, String sql) {
        return getTableUri(table).buildUpon().appendQueryParameter(PARAMETER_RAW_SQL, sql).build();
    }

    public static Uri appendNotifyUri(Uri uri, String notifyUri) {
        if (uri == null) {
            return null;
        }
        if (TextUtils.isEmpty(notifyUri)) {
            return uri;
        }
        return uri.buildUpon().appendQueryParameter(PARAMETER_NOTIFY_URI, notifyUri).build();
    }

    @Override
    public boolean onCreate() {
        setSQLiteOpenHelper(new DBHelper(getContext()));
//        setSQLiteOpenHelper( new DaoMaster.DevOpenHelper(context, DBManager.dbName, null));
        return true;
    }
}
