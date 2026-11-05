package com.openapi.core.emoji;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.openapi.core.util.LogUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;


/**
 * describe: 表情数据库操作
 * author: Went_Gone
 * create on: 2016/10/27
 */
public class EmojiDao {
    private static final String TAG = "EmojiDao";
    private static final String DB_NAME = "emoji.db";
    private static EmojiDao dao;

    public static EmojiDao getInstance() {
        if (dao == null) {
            synchronized (EmojiDao.class) {
                if (dao == null) {
                    dao = new EmojiDao();
                }
            }
        }
        return dao;
    }

    private EmojiDao() {
    }

    public List<EmojiBean> getEmojiBean(Context context) {
        List<EmojiBean> emojiBeanList = new ArrayList<>();
        if (context == null) {
            return emojiBeanList;
        }

        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            String path = ensureDatabase(context.getApplicationContext());
            db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY);
            cursor = db.query("emoji", new String[]{"unicodeInt", "_id"}, null, null, null, null, null);
            while (cursor.moveToNext()) {
                EmojiBean bean = new EmojiBean();
                bean.setUnicodeInt(cursor.getInt(0));
                bean.setId(cursor.getInt(1));
                emojiBeanList.add(bean);
            }
        } catch (Exception e) {
            LogUtil.e(TAG + " getEmojiBean: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (db != null) {
                db.close();
            }
        }
        return emojiBeanList;
    }

    private static String ensureDatabase(Context context) throws IOException {
        File dbFile = context.getDatabasePath(DB_NAME);
        if (!isValidEmojiDatabase(dbFile)) {
            if (dbFile.exists() && !dbFile.delete()) {
                LogUtil.w(TAG + " failed to delete invalid emoji db");
            }
            copyFromAssets(context, dbFile);
        }
        LogUtil.i(TAG + " emoji db path: " + dbFile.getPath());
        return dbFile.getPath();
    }

    private static boolean isValidEmojiDatabase(File dbFile) {
        if (dbFile == null || !dbFile.exists() || dbFile.length() == 0) {
            return false;
        }
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            cursor = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='emoji'",
                    null
            );
            return cursor != null && cursor.moveToFirst();
        } catch (Exception e) {
            LogUtil.w(TAG + " invalid emoji db: " + e.getMessage());
            return false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (db != null) {
                db.close();
            }
        }
    }

    private static void copyFromAssets(Context context, File dbFile) throws IOException {
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("cannot create databases dir: " + parent.getPath());
        }

        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = context.getAssets().open(DB_NAME);
            outputStream = new FileOutputStream(dbFile, false);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            outputStream.flush();
            LogUtil.i(TAG + " emoji db copied from assets");
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }
}
